# Logback 1.5.16 conditional scripts optionally use Janino. Applications using
# <if condition="..."> must supply Janino; these rules only suppress absent APIs.
-dontwarn org.codehaus.janino.ClassBodyEvaluator
-dontwarn org.codehaus.commons.compiler.CompileException
