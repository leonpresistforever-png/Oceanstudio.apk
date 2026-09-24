package studio.ocean.app;

import java.util.*;

public final class StudioScene {
  public static final class Node {
    public final long id; public String name,type;
    public float x,y,z,rx,ry,rz,sx=1,sy=1,sz=1;
    public boolean visible=true,locked=false;
    Node(long i,String n,String t){id=i;name=n;type=t;}
  }
  private final LinkedHashMap<Long,Node> nodes=new LinkedHashMap<>();
  private final ArrayDeque<String> undo=new ArrayDeque<>(), redo=new ArrayDeque<>();
  private long next=1; private Long selected;
  public StudioScene(){add("Baseplate","plane");add("Spawn","spawn");undo.clear();}
  public Node add(String name,String type){Node n=new Node(next++,name,type);nodes.put(n.id,n);selected=n.id;record("add:"+n.id);return n;}
  public boolean remove(long id){if(!nodes.containsKey(id))return false;record("remove:"+id);nodes.remove(id);if(Objects.equals(selected,id))selected=null;return true;}
  public Node selected(){return selected==null?null:nodes.get(selected);}
  public void select(long id){if(nodes.containsKey(id))selected=id;}
  public Collection<Node> all(){return Collections.unmodifiableCollection(nodes.values());}
  public int size(){return nodes.size();}
  public void transform(long id,float x,float y,float z,float rx,float ry,float rz,float sx,float sy,float sz){Node n=nodes.get(id);if(n==null||n.locked)return;record("transform:"+id+":"+n.x+":"+n.y+":"+n.z+":"+n.rx+":"+n.ry+":"+n.rz+":"+n.sx+":"+n.sy+":"+n.sz);n.x=x;n.y=y;n.z=z;n.rx=rx;n.ry=ry;n.rz=rz;n.sx=sx;n.sy=sy;n.sz=sz;}
  private void record(String op){undo.push(op);while(undo.size()>100)undo.removeLast();redo.clear();}
  public boolean canUndo(){return !undo.isEmpty();} public boolean canRedo(){return !redo.isEmpty();}
  public String snapshot(){StringBuilder b=new StringBuilder();b.append("{\"nodes\":[");boolean first=true;for(Node n:nodes.values()){if(!first)b.append(',');first=false;b.append("{\"id\":").append(n.id).append(",\"name\":\"").append(escape(n.name)).append("\",\"type\":\"").append(escape(n.type)).append("\",\"p\":[").append(n.x).append(',').append(n.y).append(',').append(n.z).append("],\"r\":[").append(n.rx).append(',').append(n.ry).append(',').append(n.rz).append("],\"s\":[").append(n.sx).append(',').append(n.sy).append(',').append(n.sz).append("]}");}return b.append("]}").toString();}
  private String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
}