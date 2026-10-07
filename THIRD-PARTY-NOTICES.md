# Third-party components

The standalone React-Fuscator JAR bundles the following unmodified dependencies:

| Component | Version | License | Upstream |
|---|---|---|---|
| ASM core/tree/commons/analysis/util | 9.10.1 | BSD-3-Clause | https://asm.ow2.io/ |
| Gson | 2.13.2 | Apache-2.0 | https://github.com/google/gson |
| SnakeYAML | 2.4 | Apache-2.0 | https://bitbucket.org/snakeyaml/snakeyaml |
| picocli | 4.7.7 | Apache-2.0 | https://github.com/remkop/picocli |
| FlatLaf / FlatLaf Extras | 3.6.1 | Apache-2.0 | https://github.com/JFormDesigner/FlatLaf |
| JSVG | 2.0.0 | MIT | https://github.com/weisj/jsvg |
| Error Prone annotations | 2.41.0 | Apache-2.0 | https://github.com/google/error-prone |

Dependency license texts are retained in `META-INF/LICENSE` and `META-INF/licenses/` in the standalone JAR. The shade build merges colliding LICENSE/NOTICE resources. Source URLs and full license files are also distributed with the project. JUnit is a test-only dependency and is not bundled.

Skidfuscator was studied as an architectural reference. React-Fuscator does not include Skidfuscator or MapleIR source/binaries. Minecraft, Paper, Fabric API and the supplied input artifacts are used only for local integration testing and are not bundled in the application.
