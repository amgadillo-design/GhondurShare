package com.ghondur.share;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String BASE="https://ghondur.com/api/";
    private final ExecutorService ex=Executors.newFixedThreadPool(4);
    private final List<Cat> mains=new ArrayList<>(), subs=new ArrayList<>();
    private Set<String> shared;
    private Spinner mainSpin,subSpin;
    private LinearLayout cards;
    private TextView status;
    private CheckBox onlyNew;
    private int selected=0,page=0;
    private boolean end=false;

    @Override public void onCreate(Bundle b){
        super.onCreate(b); getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        shared=new HashSet<>(getSharedPreferences("share",MODE_PRIVATE).getStringSet("ids",new HashSet<>()));
        setContentView(ui()); loadMain();
    }

    private View ui(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView title=t("غندور • مشاركة البطاقات",23); title.setGravity(Gravity.CENTER); root.addView(title,mw());
        mainSpin=new Spinner(this); subSpin=new Spinner(this); root.addView(mainSpin,mw()); root.addView(subSpin,mw());
        onlyNew=new CheckBox(this); onlyNew.setText("اعرض غير المنشورة فقط"); onlyNew.setChecked(true); root.addView(onlyNew,mw());
        LinearLayout tools=new LinearLayout(this);
        Button refresh=b("تحديث"); refresh.setOnClickListener(v->reload()); tools.addView(refresh,new LinearLayout.LayoutParams(0,dp(46),1));
        Button reset=b("تصفير سجل النشر"); reset.setOnClickListener(v->{shared.clear();save();reload();}); LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(0,dp(46),1); rlp.setMarginStart(dp(6)); tools.addView(reset,rlp); root.addView(tools,mw());
        status=t("جاري التحميل…",14); root.addView(status,mw());
        ScrollView sv=new ScrollView(this); cards=new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL); sv.addView(cards,mw()); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        Button more=b("تحميل المزيد"); more.setOnClickListener(v->loadCards()); root.addView(more,new LinearLayout.LayoutParams(-1,dp(50))); more.setTag("more");
        mainSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(pos<mains.size())loadSubs(mains.get(pos).id);}public void onNothingSelected(AdapterView<?> p){}});
        subSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(pos<subs.size()){selected=subs.get(pos).id;reload();}}public void onNothingSelected(AdapterView<?> p){}});
        onlyNew.setOnCheckedChangeListener((v,c)->reload());
        return root;
    }

    private void loadMain(){
        status.setText("جاري تحميل التصنيفات…"); ex.execute(()->{try{JSONObject o=new JSONObject(ApiClient.getText(BASE+"get_main_categories.php")); List<Cat> a=parse(o.optJSONArray("data")); runOnUiThread(()->{mains.clear();mains.addAll(a);mainSpin.setAdapter(adapter(a));status.setText(a.isEmpty()?"لا توجد تصنيفات":"اختار التصنيف");});}catch(Exception e){err(e);}});
    }

    private void loadSubs(int parent){
        status.setText("جاري تحميل التصنيفات الفرعية…"); ex.execute(()->{try{JSONObject o=new JSONObject(ApiClient.getText(BASE+"get_subcategories.php?parent_id="+parent)); List<Cat> a=parse(o.optJSONArray("data")); if(a.isEmpty()){for(Cat c:mains)if(c.id==parent){a.add(new Cat(parent,c.name));break;}} List<Cat> f=a; runOnUiThread(()->{subs.clear();subs.addAll(f);subSpin.setAdapter(adapter(f));if(!f.isEmpty()){selected=f.get(0).id;reload();}});}catch(Exception e){err(e);}});
    }

    private void reload(){if(selected<=0)return;page=0;end=false;cards.removeAllViews();loadCards();}

    private void loadCards(){
        if(selected<=0||end)return; int cat=selected,next=page+1; status.setText("جاري تحميل البطاقات…");
        ex.execute(()->{try{JSONObject o=new JSONObject(ApiClient.getText(BASE+"get_cards.php?category_id="+cat+"&page="+next)); JSONArray a=o.optJSONArray("data"); List<Card> list=new ArrayList<>(); if(a!=null)for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i); if(x==null)continue; int id=x.optInt("id"); String u=x.optString("image_mid"); if(u.isEmpty()||"null".equalsIgnoreCase(u))u=x.optString("image_default"); u=imageUrl(u); if(id>0&&!u.isEmpty()&&(!onlyNew.isChecked()||!shared.contains(String.valueOf(id))))list.add(new Card(id,u));} boolean last=a==null||a.length()<10; runOnUiThread(()->{if(cat!=selected)return;page=next;end=last;for(Card c:list)cards.addView(cardView(c),margin());status.setText(list.isEmpty()&&last?"لا توجد بطاقات غير منشورة":"تم التحميل • المنشور على هذا الهاتف: "+shared.size()); View more=findViewWithTag(rootView(),"more"); if(more!=null)more.setVisibility(end?View.GONE:View.VISIBLE);});}catch(Exception e){err(e);}});
    }

    private View cardView(Card c){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(8),dp(8),dp(8));
        TextView id=t("بطاقة #"+c.id+(shared.contains(String.valueOf(c.id))?" ✓":""),15);box.addView(id,mw());
        ImageView img=new ImageView(this);img.setAdjustViewBounds(true);img.setMinimumHeight(dp(220));img.setScaleType(ImageView.ScaleType.FIT_CENTER);box.addView(img,mw());loadImage(c.url,img);
        LinearLayout row=new LinearLayout(this);
        Button wa=b("واتساب");wa.setOnClickListener(v->share(c,new String[]{"com.whatsapp","com.whatsapp.w4b"}));row.addView(wa,w());
        Button tg=b("تليجرام");tg.setOnClickListener(v->share(c,new String[]{"org.telegram.messenger"}));LinearLayout.LayoutParams lp=w();lp.setMarginStart(dp(5));row.addView(tg,lp);
        Button any=b("مشاركة");any.setOnClickListener(v->share(c,null));LinearLayout.LayoutParams ap=w();ap.setMarginStart(dp(5));row.addView(any,ap);box.addView(row,mw());return box;
    }

    private void loadImage(String u,ImageView iv){ex.execute(()->{try{byte[] d=ApiClient.getBytes(u).data;Bitmap bm=BitmapFactory.decodeByteArray(d,0,d.length);runOnUiThread(()->iv.setImageBitmap(bm));}catch(Exception ignored){}});}

    private void share(Card c,String[] pkgs){
        Toast.makeText(this,"جاري تجهيز الصورة…",Toast.LENGTH_SHORT).show(); ex.execute(()->{try{ApiClient.DownloadedFile d=ApiClient.getBytes(c.url);String mime=(d.contentType!=null&&d.contentType.startsWith("image/"))?d.contentType.split(";")[0]:"image/jpeg";File dir=new File(getCacheDir(),"shared");dir.mkdirs();File f=new File(dir,"ghondur_"+c.id+(mime.contains("png")?".png":".jpg"));try(FileOutputStream out=new FileOutputStream(f)){out.write(d.data);}Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);runOnUiThread(()->launch(c.id,uri,mime,pkgs));}catch(Exception e){err(e);}});
    }

    private void launch(int id,Uri uri,String mime,String[] pkgs){
        Intent base=new Intent(Intent.ACTION_SEND);base.setType(mime);base.putExtra(Intent.EXTRA_STREAM,uri);base.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);boolean ok=false;
        if(pkgs!=null)for(String p:pkgs){try{Intent x=new Intent(base);x.setPackage(p);startActivity(x);ok=true;break;}catch(ActivityNotFoundException ignored){}}
        if(!ok){try{startActivity(Intent.createChooser(base,"انشر البطاقة عبر"));ok=true;}catch(Exception ignored){}}
        if(ok){shared.add(String.valueOf(id));save();if(onlyNew.isChecked())reload();}
    }

    private String imageUrl(String u){if(u==null||u.isEmpty()||"null".equalsIgnoreCase(u))return "";u=u.replace(" ","%20");if(u.startsWith("http://")||u.startsWith("https://"))return u;return "https://ghondur.com/uploads/images/"+u;}
    private List<Cat> parse(JSONArray a){List<Cat> r=new ArrayList<>();if(a==null)return r;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optInt("id")>0)r.add(new Cat(o.optInt("id"),o.optString("name","تصنيف")));}return r;}
    private ArrayAdapter<String> adapter(List<Cat> a){List<String> n=new ArrayList<>();for(Cat c:a)n.add(c.name);return new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,n);}
    private void save(){SharedPreferences p=getSharedPreferences("share",MODE_PRIVATE);p.edit().putStringSet("ids",new HashSet<>(shared)).apply();}
    private void err(Exception e){runOnUiThread(()->{status.setText("خطأ: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));Toast.makeText(this,status.getText(),Toast.LENGTH_LONG).show();});}
    private View rootView(){return getWindow().getDecorView();}
    private View findViewWithTag(View v,String tag){return v.findViewWithTag(tag);}
    private TextView t(String s,int z){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setPadding(0,dp(5),0,dp(5));return v;}
    private Button b(String s){Button x=new Button(this);x.setText(s);x.setAllCaps(false);return x;}
    private LinearLayout.LayoutParams mw(){return new LinearLayout.LayoutParams(-1,-2);}
    private LinearLayout.LayoutParams margin(){LinearLayout.LayoutParams x=mw();x.bottomMargin=dp(8);return x;}
    private LinearLayout.LayoutParams w(){return new LinearLayout.LayoutParams(0,dp(48),1);}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){ex.shutdownNow();super.onDestroy();}
    static class Cat{final int id;final String name;Cat(int i,String n){id=i;name=n;}}
    static class Card{final int id;final String url;Card(int i,String u){id=i;url=u;}}
}
