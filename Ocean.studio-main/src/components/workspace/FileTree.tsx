import { useState, useEffect, useCallback } from 'react';
import { ChevronRight, File, Folder } from 'lucide-react';
import { getOceanAPI } from '../../lib/platform';
import { useAppStore } from '../../store/appStore';
import './FileTree.css';

interface FileNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
}

interface TreeNodeProps {
  node: FileNode;
  depth: number;
}

function TreeNode({ node, depth }: TreeNodeProps) {
  const [expanded, setExpanded] = useState(false);
  const [children, setChildren] = useState<FileNode[]>([]);
  const [loading, setLoading] = useState(false);
  const activeFile = useAppStore((s) => s.activeFile);
  const setActiveFile = useAppStore((s) => s.setActiveFile);
  const setCenterView = useAppStore((s) => s.setCenterView);

  const loadChildren = useCallback(async () => {
    if (node.type !== 'directory') return;
    setLoading(true);
    try {
      const api = getOceanAPI();
      const items = (await api.fs.readDir(node.path)) as FileNode[];
      setChildren(items);
    } catch {
      setChildren([]);
    } finally {
      setLoading(false);
    }
  }, [node.path, node.type]);

  useEffect(() => {
    if (expanded && children.length === 0) {
      loadChildren();
    }
  }, [expanded, children.length, loadChildren]);

  function handleClick() {
    if (node.type === 'directory') {
      setExpanded(!expanded);
    } else {
      setActiveFile(node.path);
      setCenterView('editor');
    }
  }

  const isActive = activeFile === node.path;

  return (
    <div className="file-tree-item">
      <div className={`file-tree-row ${isActive ? 'active' : ''}`} onClick={handleClick}>
        {node.type === 'directory' ? (
          <ChevronRight size={14} className={`chevron ${expanded ? 'expanded' : ''}`} />
        ) : (
          <span style={{ width: 14 }} />
        )}
        {node.type === 'directory' ? <Folder size={14} /> : <File size={14} />}
        <span className="file-tree-name">{node.name}</span>
      </div>
      {expanded && node.type === 'directory' && (
        <div className="file-tree-children">
          {loading && <div className="file-tree-row" style={{ paddingLeft: 20 }}>Loading...</div>}
          {children.map((child) => (
            <TreeNode key={child.path} node={child} depth={depth + 1} />
          ))}
        </div>
      )}
    </div>
  );
}

interface Props {
  rootPath: string;
}

export default function FileTree({ rootPath }: Props) {
  const [nodes, setNodes] = useState<FileNode[]>([]);

  useEffect(() => {
    async function load() {
      const api = getOceanAPI();
      const items = (await api.fs.readDir(rootPath)) as FileNode[];
      setNodes(items);
    }
    load();
  }, [rootPath]);

  return (
    <div className="file-tree">
      {nodes.map((node) => (
        <TreeNode key={node.path} node={node} depth={0} />
      ))}
    </div>
  );
}
