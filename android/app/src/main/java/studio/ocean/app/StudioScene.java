package studio.ocean.app;

import java.util.*;

public final class StudioScene {
  public static final class Node {
    public final long id; public String name,type;
    public float x,y,z,rx,ry,rz,sx=1,sy=1,sz=1;
    public boolean visible=true,locked=false;
    Node(long i,String n,String t){id=i;name=n;type=t;}
    Node copy(){Node q=new Node(id,name,type);q.x=x;q.y=y;q.z=z;q.rx=rx;q.ry=ry;q.rz=rz;q.sx=sx;q.sy=sy;q.sz=sz;q.visible=visible;q.locked=locked;return q;}
  }
  private final LinkedHashMap<Long,Node> nodes=new LinkedHashMap<>();
  private final ArrayDeque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
  private long next=1; private Long selected;
  static final class State {LinkedHashMap<Long,Node> nodes=new LinkedHashMap<>();long next;Long selected;}
  public StudioScene(){rawAdd("Baseplate","plane");rawAdd("Spawn","spawn");undo.clear();}
  private Node rawAdd(String name,String type){Node n=new Node(next++,name,type);nodes.put(n.id,n);selected=n.id;return n;}
  private State capture(){State s=new State();for(Node n:nodes.values())s.nodes.put(n.id,n.copy());s.next=next;s.selected=selected;return s;}
  private void restore(State s){nodes.clear();for(Node n:s.nodes.values())nodes.put(n.id,n.copy());next=s.next;selected=s.selected;}
  private void checkpoint(){undo.push(capture());while(undo.size()>100)undo.removeLast();redo.clear();}
  public Node add(String name,String type){checkpoint();return rawAdd(name,type);}
  public boolean remove(long id){if(!nodes.containsKey(id))return false;checkpoint();nodes.remove(id);if(Objects.equals(selected,id))selected=null;return true;}
  public Node selected(){return selected==null?null:nodes.get(selected);}
  public void select(long id){if(nodes.containsKey(id))selected=id;}
  public Collection<Node> all(){return Collections.unmodifiableCollection(nodes.values());}
  public int size(){return nodes.size();}
  public void transform(long id,float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz){Node n=nodes.get(id);if(n==null||n.locked)return;checkpoint();n.x=x;n.y=y;n.z=z;n.rx=rx;n.ry=ry;n.rz=rz;n.sx=sx;n.sy=sy;n.sz=sz;}
  public boolean undo(){if(undo.isEmpty())return false;redo.push(capture());restore(undo.pop());return true;}
  public boolean redo(){if(redo.isEmpty())return false;undo.push(capture());restore(redo.pop());return true;}
  public boolean canUndo(){return !undo.isEmpty();}public boolean canRedo(){return !redo.isEmpty();}
  public String snapshot(){StringBuilder b=new StringBuilder();b.append("{\"nodes\":[");boolean first=true;for(Node n:nodes.values()){if(!first)b.append(',');first=false;b.append("{\"id\":").append(n.id).append(",\"name\":\"").append(escape(n.name)).append("\",\"type\":\"").append(escape(n.type)).append("\",\"p\":[").append(n.x).append(',').append(n.y).append(',').append(n.z).append("],\"r\":[").append(n.rx).append(',').append(n.ry).append(',').append(n.rz).append("],\"s\":[").append(n.sx).append(',').append(n.sy).append(',').append(n.sz).append("],\"visible\":").append(n.visible).append(",\"locked\":").append(n.locked).append("}");}return b.append("]}").toString();}
  private String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
}