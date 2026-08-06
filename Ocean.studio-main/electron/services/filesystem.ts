import fs from 'fs/promises';
import path from 'path';
import { existsSync, statSync } from 'fs';

export interface FileNode {
  name: string;
  path: string;
  type: 'file' | 'directory';
  children?: FileNode[];
}

export class FileSystemService {
  async readDirectory(dirPath: string): Promise<FileNode[]> {
    if (!existsSync(dirPath)) {
      await fs.mkdir(dirPath, { recursive: true });
      return [];
    }

    const entries = await fs.readdir(dirPath, { withFileTypes: true });
    const nodes: FileNode[] = [];

    const sorted = entries.sort((a, b) => {
      if (a.isDirectory() && !b.isDirectory()) return -1;
      if (!a.isDirectory() && b.isDirectory()) return 1;
      return a.name.localeCompare(b.name);
    });

    for (const entry of sorted) {
      if (entry.name.startsWith('.') && entry.name !== '.env') continue;
      const fullPath = path.join(dirPath, entry.name);
      const node: FileNode = {
        name: entry.name,
        path: fullPath,
        type: entry.isDirectory() ? 'directory' : 'file',
      };
      nodes.push(node);
    }

    return nodes;
  }

  async readFile(filePath: string): Promise<string> {
    if (!existsSync(filePath)) throw new Error(`File not found: ${filePath}`);
    const stat = statSync(filePath);
    if (stat.size > 5 * 1024 * 1024) throw new Error('File too large to open');
    return fs.readFile(filePath, 'utf-8');
  }

  async writeFile(filePath: string, content: string): Promise<void> {
    const dir = path.dirname(filePath);
    if (!existsSync(dir)) await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(filePath, content, 'utf-8');
  }
}
