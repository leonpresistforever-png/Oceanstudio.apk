package studio.ocean.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.res.Configuration;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Playground is intentionally a separate full-screen product surface rather than
 * another MainActivity page. It owns its navigation, composer and attachment state.
 */
public final class PlaygroundActivity extends Activity {
    private static final int PICK_ATTACHMENTS = 4201;
    private static final int BLACK = 0xFF000000;
    private static final int PANEL = 0xFF101010;
    private static final int PANEL_2 = 0xFF171717;
    private static final int BORDER = 0xFF303030;
    private static final int WHITE = 0xFFF4F4F4;
    private static final int MUTED = 0xFF929292;

    private FrameLayout root;
    private LinearLayout drawer;
    private View scrim;
    private LinearLayout attachmentStrip;
    private TextView modelButton;
    private final List<Uri> attachments = new ArrayList<>();

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BLACK);
        getWindow().setNavigationBarColor(BLACK);
        if (android.os.Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(0);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null) controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        build();
    }

    private GradientDrawable bg(int color, float radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView pill(String value) {
        TextView t = text(value, 13, WHITE);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14),0,dp(14),0);
        t.setBackground(bg(PANEL_2, 16, BORDER));
        return t;
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BLACK);
        setContentView(root);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BLACK);
        root.addView(page, new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(6), dp(12), dp(4));
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(60)));

        TextView menu = pill("☰");
        menu.setTextSize(20);
        menu.setContentDescription("Open Playground tools");
        menu.setOnClickListener(v -> openDrawer());
        top.addView(menu, new LinearLayout.LayoutParams(dp(44), dp(40)));

        TextView title = text("PLAYGROUND", 11, 0xFF707070);
        title.setLetterSpacing(.18f);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0,-2,1);
        titleLp.leftMargin=dp(12);
        top.addView(title,titleLp);

        TextView studio = pill("◇  Studio");
        studio.setTextColor(0xFFB2B2B2);
        studio.setContentDescription("Studio");
        studio.setOnClickListener(v -> startActivity(new Intent(this, OceanStudio3DActivity.class)));
        top.addView(studio,new LinearLayout.LayoutParams(-2,dp(38)));

        FrameLayout body = new FrameLayout(this);
        page.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(22),0,dp(22),dp(18));
        body.addView(hero,new FrameLayout.LayoutParams(-1,-1));

        PlaygroundMark mark = new PlaygroundMark(this);
        hero.addView(mark,new LinearLayout.LayoutParams(dp(76),dp(76)));

        TextView heading=text("What will you build?",30,WHITE);
        heading.setGravity(Gravity.CENTER);
        heading.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        LinearLayout.LayoutParams hLp=new LinearLayout.LayoutParams(-1,-2); hLp.topMargin=dp(22);
        hero.addView(heading,hLp);

        TextView sub=text("Create with models, files and assets in a focused workspace.",14,MUTED);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sLp=new LinearLayout.LayoutParams(-1,-2); sLp.topMargin=dp(9);
        hero.addView(sub,sLp);

        LinearLayout prompts=new LinearLayout(this);
        prompts.setGravity(Gravity.CENTER);
        prompts.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pLp=new LinearLayout.LayoutParams(-2,dp(38)); pLp.topMargin=dp(22);
        hero.addView(prompts,pLp);
        String[] starters={"Build a scene","Prototype an idea","Work with an asset"};
        for(String s:starters){
            TextView p=pill(s);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-1); lp.leftMargin=dp(5); lp.rightMargin=dp(5);
            prompts.addView(p,lp);
            p.setOnClickListener(v -> {
                EditText input=findViewById(0x4f434e01);
                if(input!=null){ input.setText(((TextView)v).getText()+" "); input.requestFocus(); }
            });
        }

        LinearLayout composerWrap=new LinearLayout(this);
        composerWrap.setOrientation(LinearLayout.VERTICAL);
        composerWrap.setPadding(dp(12),0,dp(12),dp(10));
        page.addView(composerWrap,new LinearLayout.LayoutParams(-1,-2));

        attachmentStrip=new LinearLayout(this);
        attachmentStrip.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView attachmentScroll=new HorizontalScrollView(this);
        attachmentScroll.setHorizontalScrollBarEnabled(false);
        attachmentScroll.addView(attachmentStrip,new HorizontalScrollView.LayoutParams(-2,dp(36)));
        attachmentScroll.setVisibility(View.GONE);
        attachmentScroll.setId(0x4f434e02);
        composerWrap.addView(attachmentScroll,new LinearLayout.LayoutParams(-1,dp(36)));

        LinearLayout composer=new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(dp(10),dp(8),dp(10),dp(8));
        composer.setBackground(bg(PANEL,24,BORDER));
        composerWrap.addView(composer,new LinearLayout.LayoutParams(-1,-2));

        EditText input=new EditText(this);
        input.setId(0x4f434e01);
        input.setHint("Ask Playground to build something…");
        input.setHintTextColor(0xFF6E6E6E);
        input.setTextColor(WHITE);
        input.setTextSize(16);
        input.setBackgroundColor(0x00000000);
        input.setGravity(Gravity.TOP|Gravity.START);
        input.setMinHeight(dp(54));
        input.setMaxLines(6);
        input.setPadding(dp(8),dp(8),dp(8),dp(8));
        composer.addView(input,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout actions=new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        composer.addView(actions,new LinearLayout.LayoutParams(-1,dp(46)));

        TextView add=pill("+");
        add.setTextSize(22);
        add.setContentDescription("Upload photos, videos, files or 3D assets");
        add.setOnClickListener(v -> chooseFiles());
        actions.addView(add,new LinearLayout.LayoutParams(dp(42),dp(38)));

        TextView store=pill("Asset store");
        LinearLayout.LayoutParams storeLp=new LinearLayout.LayoutParams(-2,dp(38)); storeLp.leftMargin=dp(7);
        actions.addView(store,storeLp);
        store.setOnClickListener(v -> Toast.makeText(this,"Asset Store entry point ready — catalog comes next.",Toast.LENGTH_SHORT).show());

        OceanByokManager configuredManager=new OceanByokManager(this);
        String modelLabel=configuredManager.isVerified()?configuredManager.getModel():"Model";
        modelButton=pill(modelLabel+"  ⌄");
        LinearLayout.LayoutParams modelLp=new LinearLayout.LayoutParams(0,dp(38),1); modelLp.leftMargin=dp(7);
        actions.addView(modelButton,modelLp);
        modelButton.setOnClickListener(this::showModels);

        TextView send=pill("↑");
        send.setTextSize(19);
        send.setBackground(bg(WHITE,19,0));
        send.setTextColor(BLACK);
        send.setContentDescription("Send");
        LinearLayout.LayoutParams sendLp=new LinearLayout.LayoutParams(dp(40),dp(40)); sendLp.leftMargin=dp(7);
        actions.addView(send,sendLp);
        send.setOnClickListener(v -> {
            String q=input.getText().toString().trim();
            if(q.isEmpty() && attachments.isEmpty()) return;
            Toast.makeText(this,"Playground agent runtime will connect in the next phase.",Toast.LENGTH_SHORT).show();
        });

        buildDrawer();
    }

    private void buildDrawer() {
        scrim=new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setVisibility(View.GONE);
        scrim.setOnClickListener(v -> closeDrawer());
        root.addView(scrim,new FrameLayout.LayoutParams(-1,-1));

        drawer=new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(18),dp(22),dp(18),dp(18));
        drawer.setBackgroundColor(0xFF0A0A0A);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(286),-1,Gravity.START);
        root.addView(drawer,lp);
        drawer.setTranslationX(-dp(286));

        LinearLayout head=new LinearLayout(this); head.setGravity(Gravity.CENTER_VERTICAL);
        drawer.addView(head,new LinearLayout.LayoutParams(-1,dp(52)));
        PlaygroundMark mini=new PlaygroundMark(this);
        head.addView(mini,new LinearLayout.LayoutParams(dp(34),dp(34)));
        TextView label=text("Playground",18,WHITE); label.setTypeface(null,Typeface.BOLD); label.setPadding(dp(12),0,0,0);
        head.addView(label,new LinearLayout.LayoutParams(0,-1,1));
        TextView close=pill("×"); close.setTextSize(20); close.setOnClickListener(v -> closeDrawer());
        head.addView(close,new LinearLayout.LayoutParams(dp(38),dp(36)));

        TextView section=text("TOOLS",10,0xFF666666); section.setLetterSpacing(.18f); section.setPadding(dp(4),dp(22),0,dp(8));
        drawer.addView(section,new LinearLayout.LayoutParams(-1,dp(52)));

        addDrawerItem("New playground","Fresh canvas and conversation");
        addDrawerItem("Assets","Uploaded files and 3D objects");
        addDrawerItem("Scene","Workspace objects and structure");
        addDrawerItem("Inspector","Selection and properties");
        addDrawerItem("History","Playground sessions");
        addDrawerItem("Models","Runtime and model selection");

        View spacer=new View(this); drawer.addView(spacer,new LinearLayout.LayoutParams(-1,0,1));
        TextView back=pill("←  Back to OceanStudio"); back.setGravity(Gravity.CENTER_VERTICAL); back.setPadding(dp(14),0,dp(14),0);
        back.setOnClickListener(v -> finish());
        drawer.addView(back,new LinearLayout.LayoutParams(-1,dp(44)));
    }

    private void addDrawerItem(String name,String caption) {
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14),dp(8),dp(12),dp(8)); row.setBackground(bg(BLACK,14,0));
        TextView a=text(name,14,WHITE); TextView b=text(caption,11,0xFF727272);
        row.addView(a,new LinearLayout.LayoutParams(-1,dp(23))); row.addView(b,new LinearLayout.LayoutParams(-1,dp(19)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58)); lp.topMargin=dp(4); drawer.addView(row,lp);
        row.setOnClickListener(v -> Toast.makeText(this,name+" tool shell ready.",Toast.LENGTH_SHORT).show());
    }

    private void openDrawer(){
        scrim.setAlpha(0f); scrim.setVisibility(View.VISIBLE); scrim.animate().alpha(1f).setDuration(160).start();
        drawer.animate().translationX(0).setDuration(210).start();
    }
    private void closeDrawer(){
        int width=drawer.getWidth()>0?drawer.getWidth():dp(286);
        scrim.animate().alpha(0f).setDuration(150).start();
        drawer.animate().translationX(-width).setDuration(180).withEndAction(() -> scrim.setVisibility(View.GONE)).start();
    }

    private void chooseFiles() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{
                "image/*","video/*","audio/*","application/octet-stream","model/gltf-binary","model/gltf+json",
                "application/pdf","text/plain","application/zip"
        });
        startActivityForResult(i,PICK_ATTACHMENTS);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=PICK_ATTACHMENTS || resultCode!=RESULT_OK || data==null) return;
        if(data.getData()!=null) addAttachment(data.getData());
        ClipData clips=data.getClipData();
        if(clips!=null) for(int n=0;n<clips.getItemCount();n++) addAttachment(clips.getItemAt(n).getUri());
        renderAttachments();
    }

    private void addAttachment(Uri uri) {
        if(uri==null || attachments.contains(uri)) return;
        attachments.add(uri);
        try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Throwable ignored){}
    }

    private void renderAttachments() {
        attachmentStrip.removeAllViews();
        for(Uri uri:attachments){
            String label=displayName(uri);
            if(label.length()>24) label="…"+label.substring(label.length()-23);
            TextView chip=pill(label+"  ×");
            chip.setTextSize(11);
            chip.setOnClickListener(v -> { attachments.remove(uri); renderAttachments(); });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(32)); lp.rightMargin=dp(6);
            attachmentStrip.addView(chip,lp);
        }
        View scroll=findViewById(0x4f434e02);
        if(scroll!=null) scroll.setVisibility(attachments.isEmpty()?View.GONE:View.VISIBLE);
    }

    private String displayName(Uri uri) {
        String fallback=uri.getLastPathSegment();
        if(fallback==null || fallback.trim().isEmpty()) fallback="Asset";
        Cursor cursor=null;
        try {
            cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);
            if(cursor!=null && cursor.moveToFirst()){
                int idx=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(idx>=0){ String name=cursor.getString(idx); if(name!=null && !name.trim().isEmpty()) return name; }
            }
        } catch(Throwable ignored) {
        } finally { if(cursor!=null) cursor.close(); }
        return fallback;
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putParcelableArrayList("playground_attachments",new ArrayList<>(attachments));
    }

    @Override protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        ArrayList<Uri> saved=state.getParcelableArrayList("playground_attachments");
        if(saved!=null){ attachments.clear(); attachments.addAll(saved); renderAttachments(); }
    }

    private void showModels(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        OceanByokManager manager=new OceanByokManager(this);
        String configured=manager.isVerified()?manager.getModel():"Configured model";
        menu.getMenu().add(configured);
        menu.getMenu().add("Configure models in OceanStudio");
        menu.setOnMenuItemClickListener(item -> {
            if(item.getTitle().toString().startsWith("Configure")) {
                Toast.makeText(this,"Use OceanStudio Models to change providers.",Toast.LENGTH_SHORT).show();
            } else modelButton.setText(item.getTitle()+"  ⌄");
            return true;
        });
        menu.show();
    }

    @Override public void onBackPressed() {
        if(drawer!=null && drawer.getTranslationX()==0) closeDrawer(); else super.onBackPressed();
    }

    /** Original Ocean mark: a rotated solid tile with an off-axis cut-out. */
    private static final class PlaygroundMark extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        PlaygroundMark(Activity c){ super(c); }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f,s=Math.min(w,h)*.60f;
            c.save(); c.rotate(17,cx,cy);
            paint.setColor(WHITE);
            Path outer=new Path();
            outer.moveTo(cx-s*.52f,cy-s*.42f); outer.lineTo(cx+s*.44f,cy-s*.52f);
            outer.lineTo(cx+s*.52f,cy+s*.42f); outer.lineTo(cx-s*.44f,cy+s*.52f); outer.close();
            c.drawPath(outer,paint);
            paint.setColor(BLACK);
            float hole=s*.18f;
            Path cut=new Path();
            cut.moveTo(cx-hole*.95f,cy-hole);
            cut.lineTo(cx+hole,cy-hole*.78f);
            cut.lineTo(cx+hole*.78f,cy+hole);
            cut.lineTo(cx-hole,cy+hole*.82f);
            cut.close();
            c.drawPath(cut,paint);
            c.restore();
        }
    }
}
