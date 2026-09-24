package studio.ocean.app;

import java.util.*;

public final class StudioScene {
  public static final class Node {
    public final long id; public String name,type;
    public float x,y,z,rx,ry,rz,sx=1,sy=1,sz=1;
    public boolean visible=true,locked=false; public String sourcePath="",metadata="";
    Node(long i,String n,String t){id=i;name=n;type=t;}
    Node copy(){Node q=new Node(id,name,type);q.x=x;q.y=y;q.z=z;q.rx=rx;q.ry=ry;q.rz=rz;q.sx=sx;q.sy=sy;q.sz=sz;q.visible=visible;q.locked=locked;q.sourcePath=sourcePath;q.metadata=metadata;return q;}
  }
  private final LinkedHashMap<Long,Node> nodes=new LinkedHashMap<>();
  private final ArrayDeque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
  private long next=1; private Long selected;
  static final class State {LinkedHashMap<Long,Node> nodes=new LinkedHashMap<>();long next;Long selected;}
  public StudioScene(){rawAdd("Baseplate","plane");rawAdd("Spawn","spawn");Node cube=rawAdd("Starter Cube","cube");cube.x=2.5f;cube.y=.5f;undo.clear();}
  private Node rawAdd(String name,String type){Node n=new Node(next++,name,type);nodes.put(n.id,n);selected=n.id;return n;}
  private State capture(){State s=new State();for(Node n:nodes.values())s.nodes.put(n.id,n.copy());s.next=next;s.selected=selected;return s;}
  private void restore(State s){nodes.clear();for(Node n:s.nodes.values())nodes.put(n.id,n.copy());next=s.next;selected=s.selected;}
  private void checkpoint(){undo.push(capture());while(undo.size()>100)undo.removeLast();redo.clear();}
  public Node add(String name,String type){checkpoint();return rawAdd(name,type);}
  public Node addImported(String name,String type,String sourcePath){checkpoint();Node n=rawAdd(name,type);n.sourcePath=sourcePath==null?"":sourcePath;return n;}
  public boolean setMetadata(long id,String metadata){Node n=nodes.get(id);if(n==null)return false;n.metadata=metadata==null?"":metadata;return true;}
  public boolean remove(long id){if(!nodes.containsKey(id))return false;checkpoint();nodes.remove(id);if(Objects.equals(selected,id))selected=null;return true;}
  public Node selected(){return selected==null?null:nodes.get(selected);}
  public void select(long id){if(nodes.containsKey(id))selected=id;}
  public Collection<Node> all(){return Collections.unmodifiableCollection(nodes.values());}
  public int size(){return nodes.size();}
  public void transform(long id,float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz){Node n=nodes.get(id);if(n==null||n.locked)return;checkpoint();n.x=x;n.y=y;n.z=z;n.rx=rx;n.ry=ry;n.rz=rz;n.sx=sx;n.sy=sy;n.sz=sz;}
  public Node duplicateSelected(){Node n=selected();if(n==null)return null;checkpoint();Node q=rawAdd(n.name+" Copy",n.type);q.x=n.x+1f;q.y=n.y;q.z=n.z;q.rx=n.rx;q.ry=n.ry;q.rz=n.rz;q.sx=n.sx;q.sy=n.sy;q.sz=n.sz;q.visible=n.visible;q.locked=false;return q;}
  public boolean deleteSelected(){Node n=selected();return n!=null&&remove(n.id);}
  public boolean resetSelectedTransform(){Node n=selected();if(n==null||n.locked)return false;transform(n.id,0,0,0,0,0,0,1,1,1);return true;}
  public boolean offsetSelected(float dx,float dy,float dz){Node n=selected();if(n==null||n.locked)return false;transform(n.id,n.x+dx,n.y+dy,n.z+dz,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz);return true;}
  public boolean rotateSelected(float dx,float dy,float dz){Node n=selected();if(n==null||n.locked)return false;transform(n.id,n.x,n.y,n.z,n.rx+dx,n.ry+dy,n.rz+dz,n.sx,n.sy,n.sz);return true;}
  public boolean scaleSelected(float factor){Node n=selected();if(n==null||n.locked)return false;transform(n.id,n.x,n.y,n.z,n.rx,n.ry,n.rz,n.sx*factor,n.sy*factor,n.sz*factor);return true;}
  public boolean snapSelected(float step){Node n=selected();if(n==null||n.locked||step<=0)return false;transform(n.id,Math.round(n.x/step)*step,Math.round(n.y/step)*step,Math.round(n.z/step)*step,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz);return true;}
  public boolean mirrorSelectedX(){Node n=selected();if(n==null||n.locked)return false;transform(n.id,-n.x,n.y,n.z,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz);return true;}
  public boolean groundSelected(){Node n=selected();if(n==null||n.locked)return false;transform(n.id,n.x,0f,n.z,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz);return true;}
  public boolean centerSelectedXZ(){Node n=selected();if(n==null||n.locked)return false;transform(n.id,0f,n.y,0f,n.rx,n.ry,n.rz,n.sx,n.sy,n.sz);return true;}
  public boolean normalizeSelectedAngles(){Node n=selected();if(n==null||n.locked)return false;transform(n.id,n.x,n.y,n.z,normalizeDeg(n.rx),normalizeDeg(n.ry),normalizeDeg(n.rz),n.sx,n.sy,n.sz);return true;}
  private float normalizeDeg(float v){float r=v%360f;if(r>180f)r-=360f;if(r<=-180f)r+=360f;return r;}
  public boolean toggleSelectedVisibility(){Node n=selected();if(n==null)return false;checkpoint();n.visible=!n.visible;return true;}
  public boolean toggleSelectedLock(){Node n=selected();if(n==null)return false;checkpoint();n.locked=!n.locked;return true;}
  public boolean undo(){if(undo.isEmpty())return false;redo.push(capture());restore(undo.pop());return true;}
  public boolean redo(){if(redo.isEmpty())return false;undo.push(capture());restore(redo.pop());return true;}
  public boolean canUndo(){return !undo.isEmpty();}public boolean canRedo(){return !redo.isEmpty();}
  public String snapshot(){StringBuilder b=new StringBuilder();b.append("{\"nodes\":[");boolean first=true;for(Node n:nodes.values()){if(!first)b.append(',');first=false;b.append("{\"id\":").append(n.id).append(",\"name\":\"").append(escape(n.name)).append("\",\"type\":\"").append(escape(n.type)).append("\",\"p\":[").append(n.x).append(',').append(n.y).append(',').append(n.z).append("],\"r\":[").append(n.rx).append(',').append(n.ry).append(',').append(n.rz).append("],\"s\":[").append(n.sx).append(',').append(n.sy).append(',').append(n.sz).append("],\"visible\":").append(n.visible).append(",\"locked\":").append(n.locked).append(",\"sourcePath\":\"").append(escape(n.sourcePath)).append("\",\"metadata\":\"").append(escape(n.metadata)).append("\"}");}return b.append("]}").toString();}
  private String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
}