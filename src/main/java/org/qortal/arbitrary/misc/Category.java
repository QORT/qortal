package org.qortal.arbitrary.misc;

public enum Category {
    ART("Art and Design"),
    AUTOMOTIVE("Automotive"),
    BEAUTY("Beauty"),
    BOOKS("Books and Reference"),
    BUSINESS("Business"),
    COMMUNICATIONS("Communications"),
    CRYPTOCURRENCY("Cryptocurrency and Blockchain"),
    CULTURE("Culture"),
    DATING("Dating"),
    DESIGN("Design"),
    ENTERTAINMENT("Entertainment"),
    EVENTS("Events"),
    FAITH("Faith and Religion"),
    FASHION("Fashion"),
    FINANCE("Finance"),
    FOOD("Food and Drink"),
    GAMING("Gaming"),
    GEOGRAPHY("Geography"),
    HEALTH("Health"),
    HISTORY("History"),
    HOME("Home"),
    KNOWLEDGE("Knowledge Share"),
    LANGUAGE("Language"),
    LIFESTYLE("Lifestyle"),
    MANUFACTURING("Manufacturing"),
    MAPS("Maps and Navigation"),
    MUSIC("Music"),
    NEWS("News"),
    OTHER("Other"),
    PETS("Pets"),
    PHILOSOPHY("Philosophy"),
    PHOTOGRAPHY("Photography"),
    POLITICS("Politics"),
    PRODUCE("Products and Services"),
    PRODUCTIVITY("Productivity"),
    PSYCHOLOGY("Psychology"),
    QORTAL("Qortal"),
    SCIENCE("Science"),
    SELF_CARE("Self Care"),
    SELF_SUFFICIENCY("Self-Sufficiency and Homesteading"),
    SHOPPING("Shopping"),
    SOCIAL("Social"),
    SOFTWARE("Software"),
    SPIRITUALITY("Spirituality"),
    SPORTS("Sports"),
    STORYTELLING("Storytelling"),
    TECHNOLOGY("Technology"),
    TOOLS("Tools"),
    TRAVEL("Travel"),
    UNCATEGORIZED("Uncategorized"),
    VIDEO("Video"),
    WEATHER("Weather");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    /**
     * Same as valueOf() but with fallback to UNCATEGORIZED if there's no match
     * @param name
     * @return a Category (using UNCATEGORIZED if no match found), or null if null name passed
     */
    public static Category uncategorizedValueOf(String name) {
        if (name == null) {
            return null;
        }
        try {
            return Category.valueOf(name);
        }
        catch (IllegalArgumentException e) {
            return Category.UNCATEGORIZED;
        }
    }

}
