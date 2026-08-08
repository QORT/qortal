package org.qortal.data.arbitrary;

import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AdvancedStringMatcher {

    private static final Logger LOGGER = LogManager.getLogger(AdvancedStringMatcher.class);

    // Common English stop words
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
        "has", "he", "in", "is", "it", "its", "of", "on", "that", "the",
        "to", "was", "were", "will", "with", "i", "you", "your", "we",
        "they", "them", "their", "this", "these", "those", "or", "but",
        "not", "no", "can", "could", "should", "would", "have", "had",
        "do", "does", "did", "so", "if", "then", "else", "when", "where",
        "why", "how", "what", "which", "who", "whom", "whose", "am",
        "been", "being", "did", "doing", "does", "had", "have", "having",
        "may", "might", "must", "shall", "my", "our", "their", "there"
    ));
    
    // Configuration class for matcher settings
    public static class MatcherConfig {
        private double misspellingThreshold = 0.3;
        private double partialMatchThreshold = 0.6;
        private double partialPhraseThreshold = 0.7;
        private double synonymThreshold = 0.3;
        private double morphologicalThreshold = 0.6;
        private boolean caseSensitive = false;
        private boolean enableMisspellings = true;
        private boolean enablePartialMatches = true;
        private boolean enablePartialPhrases = true;
        private boolean enableSynonyms = true;
        private boolean enableMorphological = true;
        private boolean enableStopWords = true;
        private double stopWordWeight = 0.2;
        private int minWordsForPartialMatch = 2;
        private String synonymFilePath = "qortal-backup/Synonyms.json";
        private boolean autoReloadSynonyms = false;
        private long synonymReloadInterval = 300000; // 5 minutes in milliseconds
        
        // Getters and setters
        public double getMisspellingThreshold() { return misspellingThreshold; }
        public void setMisspellingThreshold(double misspellingThreshold) { 
            this.misspellingThreshold = misspellingThreshold; 
        }
        
        public double getPartialMatchThreshold() { return partialMatchThreshold; }
        public void setPartialMatchThreshold(double partialMatchThreshold) { 
            this.partialMatchThreshold = partialMatchThreshold; 
        }
        
        public double getPartialPhraseThreshold() { return partialPhraseThreshold; }
        public void setPartialPhraseThreshold(double partialPhraseThreshold) { 
            this.partialPhraseThreshold = partialPhraseThreshold; 
        }
        
        public double getSynonymThreshold() { return synonymThreshold; }
        public void setSynonymThreshold(double synonymThreshold) { 
            this.synonymThreshold = synonymThreshold; 
        }
        
        public double getMorphologicalThreshold() { return morphologicalThreshold; }
        public void setMorphologicalThreshold(double morphologicalThreshold) { 
            this.morphologicalThreshold = morphologicalThreshold; 
        }
        
        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { 
            this.caseSensitive = caseSensitive; 
        }
        
        public boolean isEnableMisspellings() { return enableMisspellings; }
        public void setEnableMisspellings(boolean enableMisspellings) { 
            this.enableMisspellings = enableMisspellings; 
        }
        
        public boolean isEnablePartialMatches() { return enablePartialMatches; }
        public void setEnablePartialMatches(boolean enablePartialMatches) { 
            this.enablePartialMatches = enablePartialMatches; 
        }
        
        public boolean isEnablePartialPhrases() { return enablePartialPhrases; }
        public void setEnablePartialPhrases(boolean enablePartialPhrases) { 
            this.enablePartialPhrases = enablePartialPhrases; 
        }
        
        public boolean isEnableSynonyms() { return enableSynonyms; }
        public void setEnableSynonyms(boolean enableSynonyms) { 
            this.enableSynonyms = enableSynonyms; 
        }
        
        public boolean isEnableMorphological() { return enableMorphological; }
        public void setEnableMorphological(boolean enableMorphological) { 
            this.enableMorphological = enableMorphological; 
        }
        
        public boolean isEnableStopWords() { return enableStopWords; }
        public void setEnableStopWords(boolean enableStopWords) { 
            this.enableStopWords = enableStopWords; 
        }
        
        public double getStopWordWeight() { return stopWordWeight; }
        public void setStopWordWeight(double stopWordWeight) { 
            this.stopWordWeight = stopWordWeight; 
        }
        
        public int getMinWordsForPartialMatch() { return minWordsForPartialMatch; }
        public void setMinWordsForPartialMatch(int minWordsForPartialMatch) { 
            this.minWordsForPartialMatch = minWordsForPartialMatch; 
        }
        
        public String getSynonymFilePath() { return synonymFilePath; }
        public void setSynonymFilePath(String synonymFilePath) { 
            this.synonymFilePath = synonymFilePath; 
        }
        
        public boolean isAutoReloadSynonyms() { return autoReloadSynonyms; }
        public void setAutoReloadSynonyms(boolean autoReloadSynonyms) { 
            this.autoReloadSynonyms = autoReloadSynonyms; 
        }
        
        public long getSynonymReloadInterval() { return synonymReloadInterval; }
        public void setSynonymReloadInterval(long synonymReloadInterval) { 
            this.synonymReloadInterval = synonymReloadInterval; 
        }
    }
    
    // Weighted word class
    private static class WeightedWord {
        final String word;
        final double weight;
        
        WeightedWord(String word, double weight) {
            this.word = word;
            this.weight = weight;
        }
    }
    
    // Morphological match result
    private static class MorphologicalMatch {
        final String baseWord;
        final String form1;
        final String form2;
        final String pattern;
        final double confidence;
        
        MorphologicalMatch(String baseWord, String form1, String form2, String pattern, double confidence) {
            this.baseWord = baseWord;
            this.form1 = form1;
            this.form2 = form2;
            this.pattern = pattern;
            this.confidence = confidence;
        }
    }
    
    // Match result class
    public static class MatchResult {
        private final String matchedString;
        private final String originalString;
        private final MatchType matchType;
        private final double similarity;
        private final List<String> context;
        private final Map<String, Object> details;
        
        public MatchResult(String matchedString, String originalString, MatchType matchType, 
                          double similarity, List<String> context) {
            this(matchedString, originalString, matchType, similarity, context, new HashMap<>());
        }
        
        public MatchResult(String matchedString, String originalString, MatchType matchType, 
                          double similarity, List<String> context, Map<String, Object> details) {
            this.matchedString = matchedString;
            this.originalString = originalString;
            this.matchType = matchType;
            this.similarity = similarity;
            this.context = context;
            this.details = details;
        }
        
        // Getters
        public String getMatchedString() { return matchedString; }
        public String getOriginalString() { return originalString; }
        public MatchType getMatchType() { return matchType; }
        public double getSimilarity() { return similarity; }
        public List<String> getContext() { return context; }
        public Map<String, Object> getDetails() { return details; }
        
        @Override
        public String toString() {
            return String.format("MatchResult{matched='%s', original='%s', type=%s, similarity=%.2f}",
                    matchedString, originalString, matchType, similarity);
        }
    }
    
    // Match type enum
    public enum MatchType {
        EXACT, CASE_INSENSITIVE, MISSPELLING, PARTIAL_WORD_MATCH, PARTIAL_PHRASE, SYNONYM, MORPHOLOGICAL
    }
    
    private final Set<String> stringCollection;
    private final Map<String, Set<String>> synonymMap;
    private final MatcherConfig config;
    private final ObjectMapper objectMapper;
    private Timer synonymReloadTimer;
    private long lastSynonymLoadTime;
    
    public AdvancedStringMatcher(Collection<String> strings, MatcherConfig config) {
        this.config = config;
        this.stringCollection = new HashSet<>();
        this.synonymMap = new HashMap<>();
        this.objectMapper = new ObjectMapper();
        
        // Initialize the collection
        for (String str : strings) {
            if (!config.isCaseSensitive()) {
                this.stringCollection.add(str.toLowerCase());
            } else {
                this.stringCollection.add(str);
            }
        }
        
        // Load synonyms from JSON file
        loadSynonymsFromFile();
        
        // Setup auto-reload if enabled
        if (config.isAutoReloadSynonyms()) {
            setupSynonymAutoReload();
        }
    }
    
    public AdvancedStringMatcher(Collection<String> strings) {
        this(strings, new MatcherConfig());
    }
    
    // Load synonyms from JSON file
    private void loadSynonymsFromFile() {
        try {
            String filePath = config.getSynonymFilePath();
            File synonymFile = new File(filePath);
            
            if (!synonymFile.exists()) {
                LOGGER.error("Synonym file not found: " + filePath);
                LOGGER.error("Creating default synonym file...");
                createDefaultSynonymFile(filePath);
                return;
            }
            
            // Read JSON file
            Map<String, List<String>> synonymData = objectMapper.readValue(
                synonymFile, 
                new TypeReference<Map<String, List<String>>>() {}
            );
            
            // Clear existing synonyms
            synonymMap.clear();
            
            // Load synonyms from JSON
            for (Map.Entry<String, List<String>> entry : synonymData.entrySet()) {
                String word = config.isCaseSensitive() ? entry.getKey() : entry.getKey().toLowerCase();
                Set<String> synonyms = new HashSet<>();
                
                for (String synonym : entry.getValue()) {
                    String processedSynonym = config.isCaseSensitive() ? synonym : synonym.toLowerCase();
                    synonyms.add(processedSynonym);
                    
                    // Add reverse mapping
                    synonymMap.computeIfAbsent(processedSynonym, k -> new HashSet<>()).add(word);
                }
                
                synonymMap.put(word, synonyms);
            }
            
            lastSynonymLoadTime = System.currentTimeMillis();
            LOGGER.info("Loaded " + synonymMap.size() + " synonym groups from " + filePath);
            
        } catch (IOException e) {
            LOGGER.error("Error loading synonyms from file: " + e.getMessage());
            LOGGER.error("Using default synonyms...");
            initializeDefaultSynonyms();
        }
    }
    
    // Create default synonym file - REDUCED TO ONLY "good" AND "bad"
    private void createDefaultSynonymFile(String filePath) {
        try {
            // Create directory if it doesn't exist
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Create reduced default synonym map with only "good" and "bad"
            Map<String, List<String>> defaultSynonyms = new HashMap<>();
            
            // Add only "good" and its synonyms
            defaultSynonyms.put("good", Arrays.asList("excellent", "great", "wonderful", "fantastic"));
            
            // Add only "bad" and its synonyms
            defaultSynonyms.put("bad", Arrays.asList("terrible", "awful", "poor", "dreadful"));
            
            // Write to file
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, defaultSynonyms);
            
            // Load the newly created file
            loadSynonymsFromFile();
            
            LOGGER.info("Created reduced default synonym file: " + filePath);
            
        } catch (IOException e) {
            LOGGER.error("Error creating default synonym file: " + e.getMessage());
            initializeDefaultSynonyms();
        }
    }
    
    // Setup automatic synonym reloading
    private void setupSynonymAutoReload() {
        if (synonymReloadTimer != null) {
            synonymReloadTimer.cancel();
        }
        
        synonymReloadTimer = new Timer("SynonymReloader", true);
        synonymReloadTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    File synonymFile = new File(config.getSynonymFilePath());
                    if (synonymFile.exists()) {
                        long lastModified = synonymFile.lastModified();
                        if (lastModified > lastSynonymLoadTime) {
                            LOGGER.info("Reloading synonyms from file...");
                            loadSynonymsFromFile();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error auto-reloading synonyms: " + e.getMessage());
                }
            }
        }, config.getSynonymReloadInterval(), config.getSynonymReloadInterval());
    }
    
    // Manual synonym reload
    public void reloadSynonyms() {
        loadSynonymsFromFile();
    }
    
    // Check if a word is a stop word
    private boolean isStopWord(String word) {
        return config.isEnableStopWords() && STOP_WORDS.contains(word);
    }
    
    // Get word weight based on whether it's a stop word
    private double getWordWeight(String word) {
        return isStopWord(word) ? config.getStopWordWeight() : 1.0;
    }
    
    // Convert string array to weighted words
    private List<WeightedWord> toWeightedWords(String[] words) {
        return Arrays.stream(words)
            .map(word -> new WeightedWord(word, getWordWeight(word)))
            .collect(Collectors.toList());
    }
    
    // Advanced morphological analysis
    private MorphologicalMatch analyzeMorphologicalRelation(String word1, String word2) {
        // Handle noun/adjective pairs: hunger/hungry
        if (word1.endsWith("er") && word2.endsWith("y") && word1.length() > 2) {
            String base1 = word1.substring(0, word1.length() - 2);
            String base2 = word2.substring(0, word2.length() - 1);
            if (base1.equals(base2)) {
                return new MorphologicalMatch(base1, word1, word2, "noun_to_adjective", 0.9);
            }
        }
        
        if (word1.endsWith("y") && word2.endsWith("er") && word2.length() > 2) {
            String base1 = word1.substring(0, word1.length() - 1);
            String base2 = word2.substring(0, word2.length() - 2);
            if (base1.equals(base2)) {
                return new MorphologicalMatch(base1, word1, word2, "adjective_to_noun", 0.9);
            }
        }
        
        // Handle verb forms: run/running
        if (word1.endsWith("ing") && word2.length() > 3) {
            String base1 = word1.substring(0, word1.length() - 3);
            if (word2.equals(base1) || word2.equals(base1 + "e")) {
                return new MorphologicalMatch(base1, word1, word2, "present_participle", 0.85);
            }
        }
        
        if (word2.endsWith("ing") && word1.length() > 3) {
            String base2 = word2.substring(0, word2.length() - 3);
            if (word1.equals(base2) || word1.equals(base2 + "e")) {
                return new MorphologicalMatch(base2, word1, word2, "present_participle", 0.85);
            }
        }
        
        // Handle past tense: walk/walked
        if (word1.endsWith("ed") && word2.length() > 2) {
            String base1 = word1.substring(0, word1.length() - 2);
            if (word2.equals(base1) || word2.equals(base1 + "e")) {
                return new MorphologicalMatch(base1, word1, word2, "past_tense", 0.85);
            }
        }
        
        if (word2.endsWith("ed") && word1.length() > 2) {
            String base2 = word2.substring(0, word2.length() - 2);
            if (word1.equals(base2) || word1.equals(base2 + "e")) {
                return new MorphologicalMatch(base2, word1, word2, "past_tense", 0.85);
            }
        }
        
        // Handle plural forms: word/words
        if (word1.endsWith("s") && word2.length() > 1) {
            String base1 = word1.substring(0, word1.length() - 1);
            if (word2.equals(base1) || (base1.endsWith("e") && word2.equals(base1.substring(0, base1.length() - 1)))) {
                return new MorphologicalMatch(base1, word1, word2, "plural", 0.8);
            }
        }
        
        if (word2.endsWith("s") && word1.length() > 1) {
            String base2 = word2.substring(0, word2.length() - 1);
            if (word1.equals(base2) || (base2.endsWith("e") && word1.equals(base2.substring(0, base2.length() - 1)))) {
                return new MorphologicalMatch(base2, word1, word2, "plural", 0.8);
            }
        }
        
        // Handle noun/adjective pairs: beauty/beautiful
        if (word1.endsWith("ful") && word2.length() > 3) {
            String base1 = word1.substring(0, word1.length() - 3);
            if (word2.equals(base1) || word2.equals(base1 + "y")) {
                return new MorphologicalMatch(base1, word1, word2, "noun_to_adjective_ful", 0.85);
            }
        }
        
        if (word2.endsWith("ful") && word1.length() > 3) {
            String base2 = word2.substring(0, word2.length() - 3);
            if (word1.equals(base2) || word1.equals(base2 + "y")) {
                return new MorphologicalMatch(base2, word1, word2, "noun_to_adjective_ful", 0.85);
            }
        }
        
        // Handle adjective/adverb pairs: quick/quickly
        if (word1.endsWith("ly") && word2.length() > 2) {
            String base1 = word1.substring(0, word1.length() - 2);
            if (word2.equals(base1)) {
                return new MorphologicalMatch(base1, word1, word2, "adjective_to_adverb", 0.85);
            }
        }
        
        if (word2.endsWith("ly") && word1.length() > 2) {
            String base2 = word2.substring(0, word2.length() - 2);
            if (word1.equals(base2)) {
                return new MorphologicalMatch(base2, word1, word2, "adjective_to_adverb", 0.85);
            }
        }
        
        // Handle noun/abstract noun pairs: happy/happiness
        if (word1.endsWith("ness") && word2.length() > 4) {
            String base1 = word1.substring(0, word1.length() - 4);
            if (word2.equals(base1) || word2.equals(base1 + "y")) {
                return new MorphologicalMatch(base1, word1, word2, "adjective_to_noun_ness", 0.85);
            }
        }
        
        if (word2.endsWith("ness") && word1.length() > 4) {
            String base2 = word2.substring(0, word2.length() - 4);
            if (word1.equals(base2) || word1.equals(base2 + "y")) {
                return new MorphologicalMatch(base2, word1, word2, "adjective_to_noun_ness", 0.85);
            }
        }
        
        // Handle noun/agent pairs: act/actor
        if (word1.endsWith("or") && word2.length() > 2) {
            String base1 = word1.substring(0, word1.length() - 2);
            if (word2.equals(base1) || word2.equals(base1 + "e")) {
                return new MorphologicalMatch(base1, word1, word2, "verb_to_agent", 0.8);
            }
        }
        
        if (word2.endsWith("or") && word1.length() > 2) {
            String base2 = word2.substring(0, word2.length() - 2);
            if (word1.equals(base2) || word1.equals(base2 + "e")) {
                return new MorphologicalMatch(base2, word1, word2, "verb_to_agent", 0.8);
            }
        }
        
        return null;
    }
    
    // Find morphological matches
    private void findMorphologicalMatches(String input, List<MatchResult> results) {
        if (!config.isEnableMorphological()) return;
        
        String[] inputWords = input.split("\\s+");
        
        for (String candidate : stringCollection) {
            String[] candidateWords = candidate.split("\\s+");
            
            // Check each word pair for morphological relationship
            for (String inputWord : inputWords) {
                for (String candidateWord : candidateWords) {
                    MorphologicalMatch morphMatch = analyzeMorphologicalRelation(inputWord, candidateWord);
                    
                    if (morphMatch != null && morphMatch.confidence >= config.getMorphologicalThreshold()) {
                        Map<String, Object> details = new HashMap<>();
                        details.put("morphologicalPattern", morphMatch.pattern);
                        details.put("baseWord", morphMatch.baseWord);
                        details.put("confidence", morphMatch.confidence);
                        details.put("form1", morphMatch.form1);
                        details.put("form2", morphMatch.form2);
                        
                        results.add(new MatchResult(candidate, input, MatchType.MORPHOLOGICAL, 
                            morphMatch.confidence, getContext(candidate), details));
                        
                        // Don't add duplicate matches for the same candidate
                        break;
                    }
                }
            }
        }
    }
    
    // Main matching method
    public List<MatchResult> findMatches(String input) {
        List<MatchResult> results = new ArrayList<>();
        String processedInput = config.isCaseSensitive() ? input : input.toLowerCase();
        
        // 1. Exact match
        if (stringCollection.contains(processedInput)) {
            Map<String, Object> details = new HashMap<>();
            details.put("stopWordsFiltered", countStopWords(processedInput));
            results.add(new MatchResult(processedInput, input, MatchType.EXACT, 1.0, 
                getContext(processedInput), details));
        }
        
        // 2. Case-insensitive match
        if (config.isCaseSensitive()) {
            String lowerInput = input.toLowerCase();
            if (stringCollection.contains(lowerInput)) {
                Map<String, Object> details = new HashMap<>();
                details.put("stopWordsFiltered", countStopWords(lowerInput));
                results.add(new MatchResult(lowerInput, input, MatchType.CASE_INSENSITIVE, 1.0,
                    getContext(lowerInput), details));
            }
        }
        
        // 3. Morphological matches
        if (config.isEnableMorphological()) {
            findMorphologicalMatches(processedInput, results);
        }
        
        // 4. Partial phrase matches
        if (config.isEnablePartialPhrases()) {
            findPartialPhraseMatches(processedInput, results);
        }
        
        // 5. Misspellings
        if (config.isEnableMisspellings()) {
            findMisspellings(processedInput, results);
        }
        
        // 6. Partial word matches
        if (config.isEnablePartialMatches()) {
            findPartialWordMatches(processedInput, results);
        }
        
        // 7. Synonym matches
        if (config.isEnableSynonyms()) {
            findSynonymMatches(processedInput, results);
        }
        
        // Sort by similarity (descending)
        results.sort((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()));
        
        return results;
    }
    
    // Count stop words in a string
    private int countStopWords(String text) {
        if (!config.isEnableStopWords()) return 0;
        
        return Arrays.stream(text.split("\\s+"))
            .mapToInt(word -> isStopWord(word) ? 1 : 0)
            .sum();
    }
    
    // Find partial phrase matches with stop word weighting
    private void findPartialPhraseMatches(String input, List<MatchResult> results) {
        String[] inputWords = input.split("\\s+");
        List<WeightedWord> weightedInputWords = toWeightedWords(inputWords);
        
        for (String candidate : stringCollection) {
            String[] candidateWords = candidate.split("\\s+");
            List<WeightedWord> weightedCandidateWords = toWeightedWords(candidateWords);
            
            if (candidateWords.length >= inputWords.length) {
                continue;
            }
            
            double phraseSimilarity = calculateWeightedPartialPhraseSimilarity(
                weightedInputWords, weightedCandidateWords);
            
            if (phraseSimilarity >= config.getPartialPhraseThreshold()) {
                Map<String, Object> details = new HashMap<>();
                details.put("stopWordsInInput", countStopWords(input));
                details.put("stopWordsInMatch", countStopWords(candidate));
                details.put("significantWords", getSignificantWords(candidate));
                
                results.add(new MatchResult(candidate, input, MatchType.PARTIAL_PHRASE, 
                    phraseSimilarity, getContext(candidate), details));
            }
        }
    }
    
    // Calculate weighted partial phrase similarity
    private double calculateWeightedPartialPhraseSimilarity(
            List<WeightedWord> longerPhrase, List<WeightedWord> shorterPhrase) {
        
        for (int i = 0; i <= longerPhrase.size() - shorterPhrase.size(); i++) {
            boolean match = true;
            double totalWeight = 0.0;
            double matchedWeight = 0.0;
            
            for (int j = 0; j < shorterPhrase.size(); j++) {
                WeightedWord longerWord = longerPhrase.get(i + j);
                WeightedWord shorterWord = shorterPhrase.get(j);
                
                totalWeight += shorterWord.weight;
                
                if (longerWord.word.equals(shorterWord.word)) {
                    matchedWeight += shorterWord.weight;
                } else {
                    double similarity = calculateSimilarity(longerWord.word, shorterWord.word);
                    if (similarity >= (1.0 - config.getMisspellingThreshold())) {
                        matchedWeight += shorterWord.weight * similarity;
                    } else {
                        // Check for morphological relation
                        MorphologicalMatch morphMatch = analyzeMorphologicalRelation(
                            longerWord.word, shorterWord.word);
                        if (morphMatch != null && morphMatch.confidence >= config.getMorphologicalThreshold()) {
                            matchedWeight += shorterWord.weight * morphMatch.confidence;
                        } else {
                            match = false;
                            break;
                        }
                    }
                }
            }
            
            if (match && totalWeight > 0) {
                return matchedWeight / totalWeight;
            }
        }
        
        return 0.0;
    }
    
    // Get significant (non-stop) words from a string
    private List<String> getSignificantWords(String text) {
        if (!config.isEnableStopWords()) {
            return Arrays.asList(text.split("\\s+"));
        }
        
        return Arrays.stream(text.split("\\s+"))
            .filter(word -> !isStopWord(word))
            .collect(Collectors.toList());
    }
    
    // Find misspellings using Levenshtein distance with stop word weighting
    private void findMisspellings(String input, List<MatchResult> results) {
        for (String candidate : stringCollection) {
            double similarity = calculateWeightedSimilarity(input, candidate);
            if (similarity >= (1.0 - config.getMisspellingThreshold()) && similarity < 1.0) {
                Map<String, Object> details = new HashMap<>();
                details.put("stopWordsInInput", countStopWords(input));
                details.put("stopWordsInMatch", countStopWords(candidate));
                
                results.add(new MatchResult(candidate, input, MatchType.MISSPELLING, 
                    similarity, getContext(candidate), details));
            }
        }
    }
    
    // Calculate weighted similarity between two strings
    private double calculateWeightedSimilarity(String s1, String s2) {
        String[] words1 = s1.split("\\s+");
        String[] words2 = s2.split("\\s+");
        
        List<WeightedWord> weightedWords1 = toWeightedWords(words1);
        List<WeightedWord> weightedWords2 = toWeightedWords(words2);
        
        double totalWeight = 0.0;
        double matchedWeight = 0.0;
        
        for (WeightedWord ww1 : weightedWords1) {
            totalWeight += ww1.weight;
            
            for (WeightedWord ww2 : weightedWords2) {
                if (ww1.word.equals(ww2.word)) {
                    matchedWeight += ww1.weight;
                    break;
                } else {
                    double similarity = calculateSimilarity(ww1.word, ww2.word);
                    if (similarity >= (1.0 - config.getMisspellingThreshold())) {
                        matchedWeight += ww1.weight * similarity;
                        break;
                    } else {
                        // Check for morphological relation
                        MorphologicalMatch morphMatch = analyzeMorphologicalRelation(ww1.word, ww2.word);
                        if (morphMatch != null && morphMatch.confidence >= config.getMorphologicalThreshold()) {
                            matchedWeight += ww1.weight * morphMatch.confidence;
                            break;
                        }
                    }
                }
            }
        }
        
        return totalWeight > 0 ? matchedWeight / totalWeight : 0.0;
    }
    
    // Find partial word matches with stop word weighting
    private void findPartialWordMatches(String input, List<MatchResult> results) {
        String[] inputWords = input.split("\\s+");
        
        if (inputWords.length < config.getMinWordsForPartialMatch()) {
            return;
        }
        
        for (String candidate : stringCollection) {
            String[] candidateWords = candidate.split("\\s+");
            
            if (candidateWords.length < config.getMinWordsForPartialMatch()) {
                continue;
            }
            
            double partialSimilarity = calculateWeightedPartialWordSimilarity(inputWords, candidateWords);
            
            if (partialSimilarity >= config.getPartialMatchThreshold()) {
                Map<String, Object> details = new HashMap<>();
                details.put("stopWordsInInput", countStopWords(input));
                details.put("stopWordsInMatch", countStopWords(candidate));
                details.put("significantWords", getSignificantWords(candidate));
                
                results.add(new MatchResult(candidate, input, MatchType.PARTIAL_WORD_MATCH, 
                    partialSimilarity, getContext(candidate), details));
            }
        }
    }
    
    // Calculate weighted partial word similarity
    private double calculateWeightedPartialWordSimilarity(String[] words1, String[] words2) {
        List<WeightedWord> weightedWords1 = toWeightedWords(words1);
        List<WeightedWord> weightedWords2 = toWeightedWords(words2);
        
        double totalWeight = weightedWords1.stream().mapToDouble(ww -> ww.weight).sum();
        double matchedWeight = 0.0;
        
        for (WeightedWord ww1 : weightedWords1) {
            for (WeightedWord ww2 : weightedWords2) {
                if (ww1.word.equals(ww2.word)) {
                    matchedWeight += ww1.weight;
                    break;
                } else {
                    double wordSimilarity = calculateSimilarity(ww1.word, ww2.word);
                    if (wordSimilarity >= (1.0 - config.getMisspellingThreshold())) {
                        matchedWeight += ww1.weight * wordSimilarity;
                        break;
                    } else {
                        // Check for morphological relation
                        MorphologicalMatch morphMatch = analyzeMorphologicalRelation(ww1.word, ww2.word);
                        if (morphMatch != null && morphMatch.confidence >= config.getMorphologicalThreshold()) {
                            matchedWeight += ww1.weight * morphMatch.confidence;
                            break;
                        }
                    }
                }
            }
        }
        
        return totalWeight > 0 ? matchedWeight / totalWeight : 0.0;
    }
    
    // Find synonym matches with stop word weighting
    private void findSynonymMatches(String input, List<MatchResult> results) {
        String[] inputWords = input.split("\\s+");
        
        for (String candidate : stringCollection) {
            String[] candidateWords = candidate.split("\\s+");
            
            double synonymSimilarity = calculateWeightedSynonymSimilarity(inputWords, candidateWords);
            
            if (synonymSimilarity > config.getSynonymThreshold()) {
                Map<String, Object> details = new HashMap<>();
                details.put("stopWordsInInput", countStopWords(input));
                details.put("stopWordsInMatch", countStopWords(candidate));
                details.put("synonymMatches", getSynonymMatches(inputWords, candidateWords));
                
                results.add(new MatchResult(candidate, input, MatchType.SYNONYM, 
                    synonymSimilarity, getContext(candidate), details));
            }
        }
    }
    
    // Calculate weighted synonym similarity
    private double calculateWeightedSynonymSimilarity(String[] words1, String[] words2) {
        List<WeightedWord> weightedWords1 = toWeightedWords(words1);
        List<WeightedWord> weightedWords2 = toWeightedWords(words2);
        
        double totalWeight = Math.max(
            weightedWords1.stream().mapToDouble(ww -> ww.weight).sum(),
            weightedWords2.stream().mapToDouble(ww -> ww.weight).sum()
        );
        
        double synonymWeight = 0.0;
        
        for (WeightedWord ww1 : weightedWords1) {
            for (WeightedWord ww2 : weightedWords2) {
                if (ww1.word.equals(ww2.word) || areSynonyms(ww1.word, ww2.word)) {
                    synonymWeight += Math.max(ww1.weight, ww2.weight);
                    break;
                } else {
                    // Check for morphological relation in synonym matching
                    MorphologicalMatch morphMatch = analyzeMorphologicalRelation(ww1.word, ww2.word);
                    if (morphMatch != null && morphMatch.confidence >= config.getMorphologicalThreshold()) {
                        synonymWeight += Math.max(ww1.weight, ww2.weight) * morphMatch.confidence;
                        break;
                    }
                }
            }
        }
        
        return totalWeight > 0 ? synonymWeight / totalWeight : 0.0;
    }
    
    // Get synonym matches between two word arrays
    private List<String> getSynonymMatches(String[] words1, String[] words2) {
        List<String> matches = new ArrayList<>();
        
        for (String word1 : words1) {
            for (String word2 : words2) {
                if (areSynonyms(word1, word2)) {
                    matches.add(word1 + " ↔ " + word2);
                }
            }
        }
        
        return matches;
    }
    
    // Check if two words are synonyms
    private boolean areSynonyms(String word1, String word2) {
        if (config.isEnableStopWords() && (isStopWord(word1) || isStopWord(word2))) {
            return false;
        }
        
        Set<String> synonyms1 = synonymMap.get(word1);
        Set<String> synonyms2 = synonymMap.get(word2);
        
        if (synonyms1 != null && synonyms1.contains(word2)) {
            return true;
        }
        
        if (synonyms2 != null && synonyms2.contains(word1)) {
            return true;
        }
        
        return false;
    }
    
    // Calculate Levenshtein distance and similarity
    private double calculateSimilarity(String s1, String s2) {
        int distance = levenshteinDistance(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());
        return maxLength == 0 ? 1.0 : (double) (maxLength - distance) / maxLength;
    }
    
    // Levenshtein distance algorithm
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + 1
                    );
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
    
    // Get context for a matched string
    private List<String> getContext(String matchedString) {
        return stringCollection.stream()
            .filter(s -> !s.equals(matchedString))
            .filter(s -> calculateWeightedSimilarity(matchedString, s) > 0.5)
            .limit(3)
            .collect(Collectors.toList());
    }
    
    // Initialize default synonyms (fallback) - REDUCED TO ONLY "good" AND "bad"
    private void initializeDefaultSynonyms() {
        // This is now only used as a fallback if JSON loading fails
        LOGGER.info("Using reduced hardcoded default synonyms as fallback...");
        
        // Add only "good" and its synonyms
        addSynonym("good", "excellent");
        addSynonym("good", "great");
        addSynonym("good", "wonderful");
        addSynonym("good", "fantastic");
        
        // Add only "bad" and its synonyms
        addSynonym("bad", "terrible");
        addSynonym("bad", "awful");
        addSynonym("bad", "poor");
        addSynonym("bad", "dreadful");
    }
    
    // Add custom synonyms
    public void addSynonym(String word, String synonym) {
        word = config.isCaseSensitive() ? word : word.toLowerCase();
        synonym = config.isCaseSensitive() ? synonym : synonym.toLowerCase();
        
        synonymMap.computeIfAbsent(word, k -> new HashSet<>()).add(synonym);
        synonymMap.computeIfAbsent(synonym, k -> new HashSet<>()).add(word);
    }
}