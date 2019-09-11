# ast_plugin
A plugin for IntelliJ IDEA which shows AST and some additional information about the selected code section:
- number of variables
- number of references to variables
- number of exceptions

**Description**
Psi file is built from the source code of the current file using the standard library of Intellij Platform. We can find the node in the ast tree corresponding to the token under the cursor. In the selected fragment we are searching the edge nodes in the ast tree for this fragment. Then we are searching lca of these nodes. The user sees lca children that are inside of the selected code fragment.
