// AUTO-GENERATED — 54 extensions. Run: node scripts/generate-extensions-catalog.mjs
import type { ExtensionDefinition } from './types';
export const BUILTIN_EXTENSIONS: ExtensionDefinition[] = [
  {
    "id": "esbenp.prettier-vscode",
    "name": "prettier-vscode",
    "displayName": "Prettier",
    "description": "Code formatter",
    "version": "1.0.0",
    "publisher": "esbenp",
    "category": "formatter",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "esbenp.prettier-vscode",
    "agentGuide": "Use **Prettier** when working with prettier.\n\nFormat document on save; use Format Document command\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "esbenp.prettier-vscode.activate",
        "title": "Activate Prettier"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "prettier",
      "formatter",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 459142,
    "rating": 4.693995756956286
  },
  {
    "id": "dbaeumer.vscode-eslint",
    "name": "vscode-eslint",
    "displayName": "ESLint",
    "description": "JavaScript/TypeScript linter",
    "version": "1.0.0",
    "publisher": "dbaeumer",
    "category": "linter",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "dbaeumer.vscode-eslint",
    "agentGuide": "Use **ESLint** when working with eslint.\n\nRun ESLint fix on save; read problems panel\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "dbaeumer.vscode-eslint.activate",
        "title": "Activate ESLint"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "eslint",
      "linter",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 210216,
    "rating": 4.633107498745669
  },
  {
    "id": "eamodio.gitlens",
    "name": "gitlens",
    "displayName": "GitLens",
    "description": "Git supercharged",
    "version": "1.0.0",
    "publisher": "eamodio",
    "category": "scm",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "eamodio.gitlens",
    "agentGuide": "Use **GitLens** when working with git.\n\nBlame, history, compare; use GitLens sidebar and commands\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "eamodio.gitlens.activate",
        "title": "Activate GitLens"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "git",
      "scm",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 357107,
    "rating": 4.757720796024976
  },
  {
    "id": "ms-python.python",
    "name": "python",
    "displayName": "Python",
    "description": "Python language support",
    "version": "1.0.0",
    "publisher": "ms-python",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-python.python",
    "agentGuide": "Use **Python** when working with python.\n\nSelect interpreter, run/debug Python files, lint with pylint/flake8\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-python.python.activate",
        "title": "Activate Python"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "python",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 135284,
    "rating": 4.62379485292027
  },
  {
    "id": "ms-python.vscode-pylance",
    "name": "vscode-pylance",
    "displayName": "Pylance",
    "description": "Fast Python language server",
    "version": "1.0.0",
    "publisher": "ms-python",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-python.vscode-pylance",
    "agentGuide": "Use **Pylance** when working with python.\n\nType checking, completions, go-to-definition for .py files\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-python.vscode-pylance.activate",
        "title": "Activate Pylance"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "python",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 302733,
    "rating": 4.678617772166528
  },
  {
    "id": "rust-lang.rust-analyzer",
    "name": "rust-analyzer",
    "displayName": "rust-analyzer",
    "description": "Rust language server",
    "version": "1.0.0",
    "publisher": "rust-lang",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "rust-lang.rust-analyzer",
    "agentGuide": "Use **rust-analyzer** when working with rust.\n\ncargo check, run, test; use rust-analyzer diagnostics\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "rust-lang.rust-analyzer.activate",
        "title": "Activate rust-analyzer"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "rust",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 85769,
    "rating": 4.713677291030424
  },
  {
    "id": "golang.go",
    "name": "go",
    "displayName": "Go",
    "description": "Go language support",
    "version": "1.0.0",
    "publisher": "golang",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "golang.go",
    "agentGuide": "Use **Go** when working with go.\n\ngo fmt, go test, delve debug; GOPATH/module aware\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "golang.go.activate",
        "title": "Activate Go"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "go",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 82767,
    "rating": 4.837407463439686
  },
  {
    "id": "ms-azuretools.vscode-docker",
    "name": "vscode-docker",
    "displayName": "Docker",
    "description": "Docker container tools",
    "version": "1.0.0",
    "publisher": "ms-azuretools",
    "category": "docker",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-azuretools.vscode-docker",
    "agentGuide": "Use **Docker** when working with docker.\n\nBuild, run, attach to containers; compose up/down\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-azuretools.vscode-docker.activate",
        "title": "Activate Docker"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "docker",
      "docker",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 438946,
    "rating": 4.860481668219554
  },
  {
    "id": "redhat.vscode-yaml",
    "name": "vscode-yaml",
    "displayName": "YAML",
    "description": "YAML language support",
    "version": "1.0.0",
    "publisher": "redhat",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "redhat.vscode-yaml",
    "agentGuide": "Use **YAML** when working with yaml.\n\nSchema validation, format YAML files\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "redhat.vscode-yaml.activate",
        "title": "Activate YAML"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "yaml",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 335566,
    "rating": 4.988990717681478
  },
  {
    "id": "bradlc.vscode-tailwindcss",
    "name": "vscode-tailwindcss",
    "displayName": "Tailwind CSS IntelliSense",
    "description": "Tailwind class completions",
    "version": "1.0.0",
    "publisher": "bradlc",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "bradlc.vscode-tailwindcss",
    "agentGuide": "Use **Tailwind CSS IntelliSense** when working with tailwind.\n\nAutocomplete Tailwind classes in HTML/JSX\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "bradlc.vscode-tailwindcss.activate",
        "title": "Activate Tailwind CSS IntelliSense"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "tailwind",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 69326,
    "rating": 4.914607714974392
  },
  {
    "id": "formulahendry.auto-rename-tag",
    "name": "auto-rename-tag",
    "displayName": "Auto Rename Tag",
    "description": "Rename paired HTML/XML tags",
    "version": "1.0.0",
    "publisher": "formulahendry",
    "category": "productivity",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "formulahendry.auto-rename-tag",
    "agentGuide": "Use **Auto Rename Tag** when working with html.\n\nRename opening tag updates closing tag\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "formulahendry.auto-rename-tag.activate",
        "title": "Activate Auto Rename Tag"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "html",
      "productivity",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 506420,
    "rating": 4.72979786241715
  },
  {
    "id": "christian-kohler.path-intellisense",
    "name": "path-intellisense",
    "displayName": "Path Intellisense",
    "description": "File path completions",
    "version": "1.0.0",
    "publisher": "christian-kohler",
    "category": "productivity",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "christian-kohler.path-intellisense",
    "agentGuide": "Use **Path Intellisense** when working with paths.\n\nAutocomplete relative import paths\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "christian-kohler.path-intellisense.activate",
        "title": "Activate Path Intellisense"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "paths",
      "productivity",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 247567,
    "rating": 4.890765653013676
  },
  {
    "id": "ms-vscode.vscode-typescript-next",
    "name": "vscode-typescript-next",
    "displayName": "TypeScript Nightly",
    "description": "TS/JS language features",
    "version": "1.0.0",
    "publisher": "ms-vscode",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode.vscode-typescript-next",
    "agentGuide": "Use **TypeScript Nightly** when working with typescript.\n\ntsc, refactor, organize imports\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode.vscode-typescript-next.activate",
        "title": "Activate TypeScript Nightly"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "typescript",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 423454,
    "rating": 4.624666639711382
  },
  {
    "id": "usernamehw.errorlens",
    "name": "errorlens",
    "displayName": "Error Lens",
    "description": "Inline error highlighting",
    "version": "1.0.0",
    "publisher": "usernamehw",
    "category": "linter",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "usernamehw.errorlens",
    "agentGuide": "Use **Error Lens** when working with diagnostics.\n\nShow errors inline in editor gutter\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "usernamehw.errorlens.activate",
        "title": "Activate Error Lens"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "diagnostics",
      "linter",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 83123,
    "rating": 4.599143752921694
  },
  {
    "id": "streetsidesoftware.code-spell-checker",
    "name": "code-spell-checker",
    "displayName": "Code Spell Checker",
    "description": "Spell check in code",
    "version": "1.0.0",
    "publisher": "streetsidesoftware",
    "category": "linter",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "streetsidesoftware.code-spell-checker",
    "agentGuide": "Use **Code Spell Checker** when working with spell.\n\nFlag typos in strings and comments\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "streetsidesoftware.code-spell-checker.activate",
        "title": "Activate Code Spell Checker"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "spell",
      "linter",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 264293,
    "rating": 4.832904643031764
  },
  {
    "id": "ms-vscode.cpptools",
    "name": "cpptools",
    "displayName": "C/C++",
    "description": "Microsoft C/C++ tools",
    "version": "1.0.0",
    "publisher": "ms-vscode",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode.cpptools",
    "agentGuide": "Use **C/C++** when working with cpp.\n\nIntelliSense, debug, CMake integration\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode.cpptools.activate",
        "title": "Activate C/C++"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "cpp",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 284806,
    "rating": 4.5026397201226604
  },
  {
    "id": "llvm-vs-code-extensions.vscode-clangd",
    "name": "vscode-clangd",
    "displayName": "clangd",
    "description": "C/C++ language server",
    "version": "1.0.0",
    "publisher": "llvm-vs-code-extensions",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "llvm-vs-code-extensions.vscode-clangd",
    "agentGuide": "Use **clangd** when working with cpp.\n\nclangd completions and diagnostics\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "llvm-vs-code-extensions.vscode-clangd.activate",
        "title": "Activate clangd"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "cpp",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 501639,
    "rating": 4.62764982779745
  },
  {
    "id": "hashicorp.terraform",
    "name": "terraform",
    "displayName": "Terraform",
    "description": "IaC language support",
    "version": "1.0.0",
    "publisher": "hashicorp",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "hashicorp.terraform",
    "agentGuide": "Use **Terraform** when working with terraform.\n\nfmt, validate, plan via terraform CLI\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "hashicorp.terraform.activate",
        "title": "Activate Terraform"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "terraform",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 168206,
    "rating": 4.8592657241193455
  },
  {
    "id": "redhat.vscode-xml",
    "name": "vscode-xml",
    "displayName": "XML",
    "description": "XML tooling",
    "version": "1.0.0",
    "publisher": "redhat",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "redhat.vscode-xml",
    "agentGuide": "Use **XML** when working with xml.\n\nFormat and validate XML with schemas\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "redhat.vscode-xml.activate",
        "title": "Activate XML"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "xml",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 70893,
    "rating": 4.885734822051305
  },
  {
    "id": "ms-kubernetes-tools.vscode-kubernetes-tools",
    "name": "vscode-kubernetes-tools",
    "displayName": "Kubernetes",
    "description": "K8s cluster management",
    "version": "1.0.0",
    "publisher": "ms-kubernetes-tools",
    "category": "docker",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-kubernetes-tools.vscode-kubernetes-tools",
    "agentGuide": "Use **Kubernetes** when working with k8s.\n\nkubectl apply, logs, port-forward from palette\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-kubernetes-tools.vscode-kubernetes-tools.activate",
        "title": "Activate Kubernetes"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "k8s",
      "docker",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 139987,
    "rating": 4.668019582041159
  },
  {
    "id": "github.copilot",
    "name": "copilot",
    "displayName": "GitHub Copilot",
    "description": "AI pair programmer",
    "version": "1.0.0",
    "publisher": "github",
    "category": "ai",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "github.copilot",
    "agentGuide": "Use **GitHub Copilot** when working with copilot.\n\nInline completions; chat via Copilot panel\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "github.copilot.activate",
        "title": "Activate GitHub Copilot"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "copilot",
      "ai",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 487932,
    "rating": 4.7746164906274915
  },
  {
    "id": "github.copilot-chat",
    "name": "copilot-chat",
    "displayName": "GitHub Copilot Chat",
    "description": "AI chat in IDE",
    "version": "1.0.0",
    "publisher": "github",
    "category": "ai",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "github.copilot-chat",
    "agentGuide": "Use **GitHub Copilot Chat** when working with copilot.\n\nAsk coding questions in sidebar chat\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "github.copilot-chat.activate",
        "title": "Activate GitHub Copilot Chat"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "copilot",
      "ai",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 242787,
    "rating": 4.7125772590260935
  },
  {
    "id": "cursor.cursor",
    "name": "cursor",
    "displayName": "Cursor",
    "description": "Cursor AI IDE integration",
    "version": "1.0.0",
    "publisher": "cursor",
    "category": "ai",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "cursor.cursor",
    "agentGuide": "Use **Cursor** when working with cursor.\n\nAgent mode, composer, @codebase context\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "cursor.cursor.activate",
        "title": "Activate Cursor"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "cursor",
      "ai",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 390844,
    "rating": 4.585164458885731
  },
  {
    "id": "google.antigravity",
    "name": "antigravity",
    "displayName": "Google Antigravity",
    "description": "Antigravity coding agent",
    "version": "1.0.0",
    "publisher": "google",
    "category": "ai",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "google.antigravity",
    "agentGuide": "Use **Google Antigravity** when working with antigravity.\n\nOAuth agent with Gemini models, tools, skills\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "google.antigravity.activate",
        "title": "Activate Google Antigravity"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "antigravity",
      "ai",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 475922,
    "rating": 4.6773626580478
  },
  {
    "id": "openai.chatgpt",
    "name": "chatgpt",
    "displayName": "ChatGPT",
    "description": "OpenAI assistant in IDE",
    "version": "1.0.0",
    "publisher": "openai",
    "category": "ai",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "openai.chatgpt",
    "agentGuide": "Use **ChatGPT** when working with openai.\n\nChat and code generation via OpenAI API\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "openai.chatgpt.activate",
        "title": "Activate ChatGPT"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "openai",
      "ai",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 495475,
    "rating": 4.966151493823674
  },
  {
    "id": "anthropic.claude-dev",
    "name": "claude-dev",
    "displayName": "Claude Dev",
    "description": "Claude Code assistant",
    "version": "1.0.0",
    "publisher": "anthropic",
    "category": "ai",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "anthropic.claude-dev",
    "agentGuide": "Use **Claude Dev** when working with claude.\n\nTerminal-native Claude agent with tool use\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "anthropic.claude-dev.activate",
        "title": "Activate Claude Dev"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "claude",
      "ai",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 66726,
    "rating": 4.713847733769542
  },
  {
    "id": "ms-vscode.makefile-tools",
    "name": "makefile-tools",
    "displayName": "Makefile Tools",
    "description": "Makefile build support",
    "version": "1.0.0",
    "publisher": "ms-vscode",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode.makefile-tools",
    "agentGuide": "Use **Makefile Tools** when working with make.\n\nConfigure and run make targets\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode.makefile-tools.activate",
        "title": "Activate Makefile Tools"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "make",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 65430,
    "rating": 4.564753133857507
  },
  {
    "id": "ms-playwright.playwright",
    "name": "playwright",
    "displayName": "Playwright Test",
    "description": "E2E testing",
    "version": "1.0.0",
    "publisher": "ms-playwright",
    "category": "testing",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-playwright.playwright",
    "agentGuide": "Use **Playwright Test** when working with playwright.\n\nRun/debug Playwright tests, record codegen\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-playwright.playwright.activate",
        "title": "Activate Playwright Test"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "playwright",
      "testing",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 45532,
    "rating": 4.526944413517139
  },
  {
    "id": "vitest.explorer",
    "name": "explorer",
    "displayName": "Vitest",
    "description": "Vitest test runner UI",
    "version": "1.0.0",
    "publisher": "vitest",
    "category": "testing",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "vitest.explorer",
    "agentGuide": "Use **Vitest** when working with vitest.\n\nRun vitest from test explorer\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "vitest.explorer.activate",
        "title": "Activate Vitest"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "vitest",
      "testing",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 68055,
    "rating": 4.84642425951883
  },
  {
    "id": "hbenl.vscode-test-explorer",
    "name": "vscode-test-explorer",
    "displayName": "Test Explorer",
    "description": "Unified test UI",
    "version": "1.0.0",
    "publisher": "hbenl",
    "category": "testing",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "hbenl.vscode-test-explorer",
    "agentGuide": "Use **Test Explorer** when working with tests.\n\nDiscover and run tests from sidebar\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "hbenl.vscode-test-explorer.activate",
        "title": "Activate Test Explorer"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "tests",
      "testing",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 131119,
    "rating": 4.675748338608381
  },
  {
    "id": "ms-vscode.hexeditor",
    "name": "hexeditor",
    "displayName": "Hex Editor",
    "description": "Binary file editor",
    "version": "1.0.0",
    "publisher": "ms-vscode",
    "category": "other",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode.hexeditor",
    "agentGuide": "Use **Hex Editor** when working with hex.\n\nView/edit binary files\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode.hexeditor.activate",
        "title": "Activate Hex Editor"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "hex",
      "other",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 174490,
    "rating": 4.688686992354768
  },
  {
    "id": "yzhang.markdown-all-in-one",
    "name": "markdown-all-in-one",
    "displayName": "Markdown All in One",
    "description": "Markdown tooling",
    "version": "1.0.0",
    "publisher": "yzhang",
    "category": "productivity",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "yzhang.markdown-all-in-one",
    "agentGuide": "Use **Markdown All in One** when working with markdown.\n\nPreview, TOC, keyboard shortcuts for MD\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "yzhang.markdown-all-in-one.activate",
        "title": "Activate Markdown All in One"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "markdown",
      "productivity",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 463507,
    "rating": 4.5256911827151285
  },
  {
    "id": "davidanson.vscode-markdownlint",
    "name": "vscode-markdownlint",
    "displayName": "markdownlint",
    "description": "Markdown linting",
    "version": "1.0.0",
    "publisher": "davidanson",
    "category": "linter",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "davidanson.vscode-markdownlint",
    "agentGuide": "Use **markdownlint** when working with markdown.\n\nFix markdown style issues\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "davidanson.vscode-markdownlint.activate",
        "title": "Activate markdownlint"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "markdown",
      "linter",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 444204,
    "rating": 4.828977733856298
  },
  {
    "id": "ms-vscode.live-server",
    "name": "live-server",
    "displayName": "Live Server",
    "description": "Local dev server",
    "version": "1.0.0",
    "publisher": "ms-vscode",
    "category": "productivity",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode.live-server",
    "agentGuide": "Use **Live Server** when working with server.\n\nLaunch static server with live reload\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode.live-server.activate",
        "title": "Activate Live Server"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "server",
      "productivity",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 141072,
    "rating": 4.793732851060192
  },
  {
    "id": "ritwickdey.liveserver",
    "name": "liveserver",
    "displayName": "Live Server (legacy)",
    "description": "HTTP server for static files",
    "version": "1.0.0",
    "publisher": "ritwickdey",
    "category": "productivity",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ritwickdey.liveserver",
    "agentGuide": "Use **Live Server (legacy)** when working with server.\n\nRight-click Open with Live Server\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ritwickdey.liveserver.activate",
        "title": "Activate Live Server (legacy)"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "server",
      "productivity",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 490623,
    "rating": 4.891141274991199
  },
  {
    "id": "prisma.prisma",
    "name": "prisma",
    "displayName": "Prisma",
    "description": "Prisma ORM support",
    "version": "1.0.0",
    "publisher": "prisma",
    "category": "database",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "prisma.prisma",
    "agentGuide": "Use **Prisma** when working with prisma.\n\nFormat schema, jump to model, run migrations\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "prisma.prisma.activate",
        "title": "Activate Prisma"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "prisma",
      "database",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 364754,
    "rating": 4.54997971012854
  },
  {
    "id": "mtxr.sqltools",
    "name": "sqltools",
    "displayName": "SQLTools",
    "description": "Database client",
    "version": "1.0.0",
    "publisher": "mtxr",
    "category": "database",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "mtxr.sqltools",
    "agentGuide": "Use **SQLTools** when working with sql.\n\nConnect PostgreSQL/MySQL/SQLite, run queries\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "mtxr.sqltools.activate",
        "title": "Activate SQLTools"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "sql",
      "database",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 274560,
    "rating": 4.583113652755765
  },
  {
    "id": "cweijan.vscode-database-client2",
    "name": "vscode-database-client2",
    "displayName": "Database Client",
    "description": "DB management UI",
    "version": "1.0.0",
    "publisher": "cweijan",
    "category": "database",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "cweijan.vscode-database-client2",
    "agentGuide": "Use **Database Client** when working with sql.\n\nGUI for MySQL, PG, Redis, MongoDB\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "cweijan.vscode-database-client2.activate",
        "title": "Activate Database Client"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "sql",
      "database",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 91627,
    "rating": 4.972613569986295
  },
  {
    "id": "graphql.vscode-graphql",
    "name": "vscode-graphql",
    "displayName": "GraphQL",
    "description": "GraphQL language support",
    "version": "1.0.0",
    "publisher": "graphql",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "graphql.vscode-graphql",
    "agentGuide": "Use **GraphQL** when working with graphql.\n\nSchema validation, go-to-definition\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "graphql.vscode-graphql.activate",
        "title": "Activate GraphQL"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "graphql",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 277881,
    "rating": 4.780638275539193
  },
  {
    "id": "apollographql.vscode-apollo",
    "name": "vscode-apollo",
    "displayName": "Apollo GraphQL",
    "description": "Apollo tooling",
    "version": "1.0.0",
    "publisher": "apollographql",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "apollographql.vscode-apollo",
    "agentGuide": "Use **Apollo GraphQL** when working with graphql.\n\nApollo schema and client helpers\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "apollographql.vscode-apollo.activate",
        "title": "Activate Apollo GraphQL"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "graphql",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 101431,
    "rating": 4.578528376597653
  },
  {
    "id": "svelte.svelte-vscode",
    "name": "svelte-vscode",
    "displayName": "Svelte",
    "description": "Svelte framework support",
    "version": "1.0.0",
    "publisher": "svelte",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "svelte.svelte-vscode",
    "agentGuide": "Use **Svelte** when working with svelte.\n\nSyntax, format, component snippets\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "svelte.svelte-vscode.activate",
        "title": "Activate Svelte"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "svelte",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 294787,
    "rating": 4.802542158196937
  },
  {
    "id": "vue.volar",
    "name": "volar",
    "displayName": "Vue - Official",
    "description": "Vue 3 language support",
    "version": "1.0.0",
    "publisher": "vue",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "vue.volar",
    "agentGuide": "Use **Vue - Official** when working with vue.\n\nVolar TS support for .vue files\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "vue.volar.activate",
        "title": "Activate Vue - Official"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "vue",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 50288,
    "rating": 4.882213968310366
  },
  {
    "id": "astro-build.astro-vscode",
    "name": "astro-vscode",
    "displayName": "Astro",
    "description": "Astro framework",
    "version": "1.0.0",
    "publisher": "astro-build",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "astro-build.astro-vscode",
    "agentGuide": "Use **Astro** when working with astro.\n\nSyntax highlighting, format .astro\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "astro-build.astro-vscode.activate",
        "title": "Activate Astro"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "astro",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 303857,
    "rating": 4.738864015679889
  },
  {
    "id": "denoland.vscode-deno",
    "name": "vscode-deno",
    "displayName": "Deno",
    "description": "Deno runtime support",
    "version": "1.0.0",
    "publisher": "denoland",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "denoland.vscode-deno",
    "agentGuide": "Use **Deno** when working with deno.\n\ndeno fmt, lint, test, LSP\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "denoland.vscode-deno.activate",
        "title": "Activate Deno"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "deno",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 403126,
    "rating": 4.7004890343356465
  },
  {
    "id": "biomejs.biome",
    "name": "biome",
    "displayName": "Biome",
    "description": "Fast formatter/linter",
    "version": "1.0.0",
    "publisher": "biomejs",
    "category": "formatter",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "biomejs.biome",
    "agentGuide": "Use **Biome** when working with biome.\n\nbiome check --write; replaces ESLint+Prettier\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "biomejs.biome.activate",
        "title": "Activate Biome"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "biome",
      "formatter",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 14134,
    "rating": 4.795104560366238
  },
  {
    "id": "charliermarsh.ruff",
    "name": "ruff",
    "displayName": "Ruff",
    "description": "Python linter/formatter",
    "version": "1.0.0",
    "publisher": "charliermarsh",
    "category": "linter",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "charliermarsh.ruff",
    "agentGuide": "Use **Ruff** when working with ruff.\n\nruff check --fix; fast Python linting\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "charliermarsh.ruff.activate",
        "title": "Activate Ruff"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "ruff",
      "linter",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 16148,
    "rating": 4.527614271749513
  },
  {
    "id": "ms-dotnettools.csharp",
    "name": "csharp",
    "displayName": "C# Dev Kit",
    "description": "C# language support",
    "version": "1.0.0",
    "publisher": "ms-dotnettools",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-dotnettools.csharp",
    "agentGuide": "Use **C# Dev Kit** when working with csharp.\n\nOmniSharp, debug .NET projects\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-dotnettools.csharp.activate",
        "title": "Activate C# Dev Kit"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "csharp",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 225627,
    "rating": 4.947981746190766
  },
  {
    "id": "fwcd.kotlin",
    "name": "kotlin",
    "displayName": "Kotlin",
    "description": "Kotlin language support",
    "version": "1.0.0",
    "publisher": "fwcd",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "fwcd.kotlin",
    "agentGuide": "Use **Kotlin** when working with kotlin.\n\nKotlin LSP for JVM/Android\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "fwcd.kotlin.activate",
        "title": "Activate Kotlin"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "kotlin",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 85786,
    "rating": 4.768222110990177
  },
  {
    "id": "vscjava.vscode-java-pack",
    "name": "vscode-java-pack",
    "displayName": "Extension Pack for Java",
    "description": "Java development bundle",
    "version": "1.0.0",
    "publisher": "vscjava",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "vscjava.vscode-java-pack",
    "agentGuide": "Use **Extension Pack for Java** when working with java.\n\nMaven, debug, test runner for Java\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "vscjava.vscode-java-pack.activate",
        "title": "Activate Extension Pack for Java"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "java",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 104204,
    "rating": 4.900220242683716
  },
  {
    "id": "ms-vscode.powershell",
    "name": "powershell",
    "displayName": "PowerShell",
    "description": "PowerShell language support",
    "version": "1.0.0",
    "publisher": "ms-vscode",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode.powershell",
    "agentGuide": "Use **PowerShell** when working with powershell.\n\nRun/debug PowerShell scripts\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode.powershell.activate",
        "title": "Activate PowerShell"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "powershell",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 96524,
    "rating": 4.716163906741919
  },
  {
    "id": "redhat.vscode-java",
    "name": "vscode-java",
    "displayName": "Language Support for Java",
    "description": "Java LSP",
    "version": "1.0.0",
    "publisher": "redhat",
    "category": "language",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "redhat.vscode-java",
    "agentGuide": "Use **Language Support for Java** when working with java.\n\nRed Hat Java language server\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "redhat.vscode-java.activate",
        "title": "Activate Language Support for Java"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "java",
      "language",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 456534,
    "rating": 4.690705771479304
  },
  {
    "id": "ms-vscode-remote.remote-ssh",
    "name": "remote-ssh",
    "displayName": "Remote - SSH",
    "description": "SSH remote development",
    "version": "1.0.0",
    "publisher": "ms-vscode-remote",
    "category": "productivity",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode-remote.remote-ssh",
    "agentGuide": "Use **Remote - SSH** when working with ssh.\n\nConnect to remote host, edit files over SSH\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode-remote.remote-ssh.activate",
        "title": "Activate Remote - SSH"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "ssh",
      "productivity",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 185375,
    "rating": 4.621055246698298
  },
  {
    "id": "ms-vscode-remote.remote-containers",
    "name": "remote-containers",
    "displayName": "Dev Containers",
    "description": "Container development",
    "version": "1.0.0",
    "publisher": "ms-vscode-remote",
    "category": "docker",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ms-vscode-remote.remote-containers",
    "agentGuide": "Use **Dev Containers** when working with devcontainer.\n\nReopen in container, devcontainer.json\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ms-vscode-remote.remote-containers.activate",
        "title": "Activate Dev Containers"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "devcontainer",
      "docker",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 354467,
    "rating": 4.834572979927106
  },
  {
    "id": "ocean.studio-agent",
    "name": "studio-agent",
    "displayName": "Ocean.studio Agent",
    "description": "Built-in Ocean coding agent",
    "version": "1.0.0",
    "publisher": "ocean",
    "category": "ai",
    "platforms": [
      "electron",
      "web"
    ],
    "installType": "openvsx",
    "source": "builtin",
    "openvsxId": "ocean.studio-agent",
    "agentGuide": "Use **Ocean.studio Agent** when working with ocean.\n\nTerminal, MCP, providers, playground integration\n\n- Activate on relevant file types\n- Prefer extension commands over manual shell when available\n- Report extension errors to user before fallback",
    "commands": [
      {
        "id": "ocean.studio-agent.activate",
        "title": "Activate Ocean.studio Agent"
      }
    ],
    "activationEvents": [
      "onStartupFinished"
    ],
    "tags": [
      "ocean",
      "ai",
      "vscode-compatible"
    ],
    "verified": true,
    "downloads": 61738,
    "rating": 4.904007107000276
  }
] as ExtensionDefinition[];
export const BUILTIN_EXTENSION_COUNT = 54;
