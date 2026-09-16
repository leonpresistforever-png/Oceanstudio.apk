package studio.ocean.app;

import java.util.Locale;

/** Only deliberately marked commands bypass the model. Natural language stays natural language. */
final class OceanAgentRequests {
    static String explicitCommand(String prompt) {
        String text = prompt.trim();
        if (text.startsWith("$ ")) return text.substring(2).trim();
        if (text.startsWith("exec ")) return text.substring(5).trim();
        if (text.startsWith("pkg ") || text.startsWith("apt ") || text.startsWith("ocean-") || text.startsWith("./")) return text;
        return null;
    }

    static boolean opensTerminal(String prompt) {
        String text = prompt.trim().toLowerCase(Locale.ROOT).replaceAll("[.!]+$", "");
        if (text.startsWith("please ")) text = text.substring(7);
        return text.equals("open terminal") || text.equals("open the terminal")
                || text.equals("open ocean terminal") || text.equals("open ocean os terminal")
                || text.equals("open up ocean os terminal");
    }
}
