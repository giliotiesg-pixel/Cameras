package br.com.centralv720;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private LinearLayout root, list;
    private SurfaceView surface;
    private MediaPlayer player;
    private TextView status;
    private String activeUrl = "";
    private final int blue = Color.rgb(20,62,112);
    @Override public void onCreate(Bundle b) { super.onCreate(b); showHome(); }
    private TextView text(String s, int size) { TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(blue);v.setPadding(12,12,12,12);return v; }
    private Button button(String s, Runnable r) { Button b=new Button(this);b.setText(s);b.setOnClickListener(v->r.run());return b; }
    private void base(String title) { root=new LinearLayout(this);root.setOrientation(1);root.setPadding(14,18,14,10);root.setBackgroundColor(Color.rgb(245,247,251));setContentView(root);root.addView(text(title,24)); }
    private JSONArray cameras() { try{return new JSONArray(getPreferences(0).getString("cameras","[]"));}catch(Exception e){return new JSONArray();} }
    private void save(JSONArray a){getPreferences(0).edit().putString("cameras",a.toString()).apply();}
    private void showHome(){stop();base("CENTRAL V720");root.addView(text("Câmeras cadastradas • Wi-Fi / Internet",15));root.addView(button("+ Adicionar câmera",()->addCamera()));ScrollView sc=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(1);sc.addView(list);root.addView(sc);JSONArray a=cameras();if(a.length()==0)list.addView(text("Nenhuma câmera cadastrada. Adicione um endereço RTSP para testar.",15));for(int i=0;i<a.length();i++){final int idx=i;JSONObject c=a.optJSONObject(i);if(c==null)continue;String name=c.optString("name","Câmera");String url=c.optString("url","");list.addView(button("▶ "+name,()->play(name,url)));list.addView(button("Excluir "+name,()->new AlertDialog.Builder(this).setMessage("Excluir câmera?").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{JSONArray n=new JSONArray();for(int j=0;j<a.length();j++)if(j!=idx)n.put(a.opt(j));save(n);showHome();}).show()));}}
    private void addCamera(){base("Adicionar câmera");EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Nome: Escritório");root.addView(name);EditText url=new EditText(this);url.setSingleLine(true);url.setHint("rtsp://IP:554/caminho");root.addView(url);root.addView(text("A câmera precisa oferecer RTSP. O aplicativo V720 pode utilizar um protocolo fechado.",14));root.addView(button("Salvar câmera",()->{String u=url.getText().toString().trim();if(!u.startsWith("rtsp://")&&!u.startsWith("rtsps://")){Toast.makeText(this,"Informe um endereço RTSP válido",Toast.LENGTH_LONG).show();return;}try{JSONObject o=new JSONObject();o.put("name",name.getText().toString().trim().isEmpty()?"Câmera":name.getText().toString().trim());o.put("url",u);JSONArray a=cameras();a.put(o);save(a);showHome();}catch(Exception e){Toast.makeText(this,"Erro ao salvar",Toast.LENGTH_LONG).show();}}));root.addView(button("Voltar",()->showHome()));}
    private void play(String name,String url){stop();activeUrl=url;base(name);surface=new SurfaceView(this);root.addView(surface,new LinearLayout.LayoutParams(-1,0,1));surface.getHolder().addCallback(this);status=text("Conectando...",15);root.addView(status);root.addView(button("Voltar às câmeras",()->showHome()));}
    @Override public void surfaceCreated(SurfaceHolder h){if(activeUrl.isEmpty())return;try{player=new MediaPlayer();player.setDisplay(h);player.setDataSource(this,android.net.Uri.parse(activeUrl));player.setOnPreparedListener(p->{status.setText("Transmitindo");p.start();});player.setOnErrorListener((p,what,extra)->{status.setText("Não foi possível abrir o vídeo. Verifique RTSP, rede e credenciais.");return true;});player.prepareAsync();}catch(Exception e){status.setText("Falha: "+e.getMessage());}}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){}
    @Override public void surfaceDestroyed(SurfaceHolder h){stop();}
    private void stop(){activeUrl="";if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    @Override protected void onDestroy(){stop();super.onDestroy();}
}
