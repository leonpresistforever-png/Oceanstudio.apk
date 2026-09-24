package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;

public final class StudioRenderSettings {
  private final SharedPreferences prefs;
  public StudioRenderSettings(Context c){prefs=c.getSharedPreferences("ocean_studio_render",Context.MODE_PRIVATE);}

  public String quality(){return prefs.getString("quality","high");}
  public void setQuality(String q){
    if(!q.equals("performance")&&!q.equals("balanced")&&!q.equals("high")&&!q.equals("ultra"))q="high";
    prefs.edit().putString("quality",q).apply();
  }
  public int previewTriangleCap(){
    switch(quality()){
      case "performance":return 75000;
      case "balanced":return 150000;
      case "ultra":return 500000;
      default:return 250000;
    }
  }
  public String qualityLabel(){
    switch(quality()){
      case "performance":return "Performance · 75k triangle previews";
      case "balanced":return "Balanced · 150k triangle previews";
      case "ultra":return "Ultra · 500k triangle previews";
      default:return "High · 250k triangle previews";
    }
  }
}