package studio.ocean.app;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

/** Small native renderer for the model's common markdown. It never renders HTML or active content. */
final class OceanMessageText {
    static CharSequence render(String source) {
        if (source == null || source.isEmpty()) return "";
        SpannableStringBuilder out = new SpannableStringBuilder();
        boolean code = false;
        for (String line : source.split("\n", -1)) {
            if (line.trim().startsWith("```")) { code = !code; continue; }
            int start = out.length();
            if (code) {
                out.append(line);
                span(out, new TypefaceSpan("monospace"), start);
                span(out, new BackgroundColorSpan(0xFFF3F4F4), start);
            } else {
                boolean heading = line.matches("#{1,6} .*?");
                if (heading) line = line.replaceFirst("^#{1,6} ", "");
                for (int i = 0; i < line.length();) {
                    String marker = line.startsWith("**", i) ? "**" : line.charAt(i) == '`' ? "`" : null;
                    int end = marker == null ? -1 : line.indexOf(marker, i + marker.length());
                    if (end > i + marker.length()) {
                        int mark = out.length(); out.append(line.substring(i + marker.length(), end));
                        span(out, marker.equals("**") ? new StyleSpan(Typeface.BOLD) : new TypefaceSpan("monospace"), mark);
                        i = end + marker.length();
                    } else { out.append(line.charAt(i)); i++; }
                }
                if (heading) span(out, new StyleSpan(Typeface.BOLD), start);
            }
            out.append('\n');
        }
        if (out.length() > 0) out.delete(out.length() - 1, out.length());
        return out;
    }
    private static void span(SpannableStringBuilder text, Object span, int start) {
        if (text.length() > start) text.setSpan(span, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
