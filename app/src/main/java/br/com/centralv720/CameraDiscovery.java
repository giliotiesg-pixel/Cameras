package br.com.centralv720;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkAddress;
import android.net.LinkProperties;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class CameraDiscovery {
    public interface Listener { void onProgress(String message); void onFound(String ip, String details); void onComplete(String summary); }
    private final Context context;
    private volatile boolean cancelled;
    private final ExecutorService executor=Executors.newFixedThreadPool(20);
    private final Set<String> reported=Collections.synchronizedSet(new HashSet<>());
    public CameraDiscovery(Context c){context=c.getApplicationContext();}
    public void cancel(){cancelled=true;executor.shutdownNow();}
    public void scan(Listener l){
        new Thread(()->{
            try{
                ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
                Network network=cm.getActiveNetwork();
                if(network==null){l.onComplete("Sem rede ativa. Conecte-se ao Wi-Fi da câmera ou ao roteador.");return;}
                NetworkCapabilities cap=cm.getNetworkCapabilities(network);
                if(cap==null||!cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){l.onComplete("Conecte o celular à rede Wi-Fi local para procurar câmeras.");return;}
                LinkProperties lp=cm.getLinkProperties(network);
                if(lp==null){l.onComplete("Não foi possível identificar a rede.");return;}
                String local=null;int prefix=24;
                for(LinkAddress a:lp.getLinkAddresses())if(a.getAddress() instanceof Inet4Address){local=a.getAddress().getHostAddress();prefix=a.getPrefixLength();break;}
                if(local==null){l.onComplete("Rede IPv4 não encontrada.");return;}
                l.onProgress("Wi-Fi: "+local+" /"+prefix+" • procurando serviços ONVIF e portas RTSP...");
                discoverOnvif(network,l);
                // Limita a 254 endereços para evitar varredura excessiva em redes grandes.
                byte[] b=InetAddress.getByName(local).getAddress();
                int base=((b[0]&255)<<24)|((b[1]&255)<<16)|((b[2]&255)<<8)|(b[3]&255);
                int mask=prefix>=24?(-1 << (32-prefix)):0xffffff00;
                int net=base&mask;
                List<Future<?>> tasks=new ArrayList<>();
                for(int host=1;host<255&&!cancelled;host++){
                    final int addr=net|host;
                    final String ip=((addr>>>24)&255)+"."+((addr>>>16)&255)+"."+((addr>>>8)&255)+"."+(addr&255);
                    if(ip.equals(local))continue;
                    tasks.add(executor.submit(()->probe(network,ip,l)));
                }
                for(Future<?> f:tasks){if(cancelled)break;try{f.get(3,TimeUnit.SECONDS);}catch(Exception ignored){}}
                if(!cancelled)l.onComplete("Busca concluída. "+reported.size()+" dispositivo(s) com serviços encontrados. Porta aberta não confirma câmera ou vídeo.");
            }catch(Exception e){l.onComplete("Erro de rede: "+e.getMessage());}
            finally{executor.shutdown();}
        },"camera-discovery").start();
    }
    private void probe(Network network,String ip,Listener l){
        if(cancelled)return;
        StringBuilder found=new StringBuilder();
        int[] ports={554,8554,80,8080,8899};
        for(int port:ports){
            if(cancelled)return;
            try(Socket socket=network.getSocketFactory().createSocket()){
                socket.connect(new InetSocketAddress(ip,port),180);
                if(found.length()>0)found.append(", ");
                found.append(port);
            }catch(Exception ignored){}
        }
        if(found.length()>0&&reported.add(ip))l.onFound(ip,"Portas TCP abertas: "+found+". RTSP/ONVIF não confirmados.");
    }
    private void discoverOnvif(Network network,Listener l){
        String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\"><e:Header><w:MessageID>uuid:"+UUID.randomUUID()+"</w:MessageID><w:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header><e:Body><d:Probe/></e:Body></e:Envelope>";
        try(MulticastSocket s=new MulticastSocket(null)){
            s.setReuseAddress(true);s.bind(new InetSocketAddress(0));s.setSoTimeout(450);
            s.setNetworkInterface(NetworkInterface.getByInetAddress(localAddress(network)));
            byte[] payload=xml.getBytes("UTF-8");
            s.send(new DatagramPacket(payload,payload.length,InetAddress.getByName("239.255.255.250"),3702));
            long end=System.currentTimeMillis()+1800;
            while(System.currentTimeMillis()<end&&!cancelled){
                byte[] buf=new byte[8192];DatagramPacket p=new DatagramPacket(buf,buf.length);
                try{s.receive(p);String data=new String(p.getData(),0,p.getLength(),"UTF-8");
                    if(data.contains("ProbeMatches")||data.contains("ProbeMatch")){
                        String ip=p.getAddress().getHostAddress();
                        if(reported.add(ip))l.onFound(ip,"Resposta WS-Discovery/ONVIF recebida; transmissão ainda não verificada.");
                    }
                }catch(SocketTimeoutException ignored){}
            }
        }catch(Exception e){l.onProgress("Busca ONVIF indisponível; verificando portas TCP...");}
    }
    private InetAddress localAddress(Network network)throws Exception{
        ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        for(LinkAddress a:cm.getLinkProperties(network).getLinkAddresses())if(a.getAddress() instanceof Inet4Address)return a.getAddress();
        throw new IOException("IPv4 indisponível");
    }
}
