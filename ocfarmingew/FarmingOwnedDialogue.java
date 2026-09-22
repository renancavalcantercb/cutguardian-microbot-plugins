package net.runelite.client.plugins.microbot.ocfarmingew;

import java.util.List;
import java.util.Locale;

/** Only selects responses for a pending action that the worker itself initiated. */
final class FarmingOwnedDialogue
{
    static String option(FarmingActionPolicy.Action action, String question, List<String> options)
    {
        String title = question == null ? "" : clean(question);
        for (String option : options)
        {
            String text = clean(option);
            boolean yes = text.matches("yes([.! ,].*)?");
            if (action == FarmingActionPolicy.Action.REMOVE_TREE
                && (yes || text.contains("would you chop my tree") || text.contains("here's 200 coins")
                    || text.contains("i'd rather pay you"))) return option;
            if (action == FarmingActionPolicy.Action.PAY && yes) return option;
            if (action == FarmingActionPolicy.Action.CLEAR && yes
                && title.matches(".*(clear|dead|remove|dig up|stump).*")) return option;
        }
        return null;
    }

    private static String clean(String value)
    {
        return value.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT).replace('\u2019', '\'');
    }
}
