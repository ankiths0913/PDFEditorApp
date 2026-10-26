# PDFBox discovers some font and resource classes reflectively.
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.gemalto.jp2.**
