# App-specific R8 rules belong here. Hilt, Kotlin serialization, Navigation 3, and LiteRT-LM
# publish their own consumer rules; avoid broad package-wide keeps that would hide release issues.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature
