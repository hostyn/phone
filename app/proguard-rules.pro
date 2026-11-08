# ez-vcard: optional integrations (jCard, hCard, templates) that this app doesn't ship.
-dontwarn com.fasterxml.jackson.**
-dontwarn freemarker.**
-dontwarn org.jsoup.**
# ez-vcard instantiates property scribes reflectively.
-keep class ezvcard.** { *; }
