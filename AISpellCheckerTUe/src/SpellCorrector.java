import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class SpellCorrector {
    final private CorpusReader cr;
    final private ConfusionMatrixReader cmr;
    final private boolean DEBUG = false;
    final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz'".toCharArray();
    
    
    public SpellCorrector(CorpusReader cr, ConfusionMatrixReader cmr) 
    {
        this.cr = cr;
        this.cmr = cmr;
    }
    
    private void printPhraseMatrix(String sentence, List<Map<String, Double>> sentenceMashups) {
        if(!DEBUG) return;
        System.out.println(sentence);
        System.out.println("-----------------------------------------------------------------------------------");
        
        for(int j = 0; j < sentenceMashups.size(); j++) {
            Map<String, Double> wordmatrix = sentenceMashups.get(j);
            Object wordsuggestions[] = wordmatrix.keySet().toArray();
            for(int i = 0; i < wordsuggestions.length; i++){
                 System.out.print(wordsuggestions[i] + "\t");
            }
            System.out.print("\n");
        }
        System.out.println("-----------------------------------------------------------------------------------");
    }
    
    /*
    The method correctPhrase deals with the correction at the
sentence level according to the noisy-channel model combined
with bigram information. For the channel probability you
may want to calibrate the weight of the prior, e.g. using a
construction like likelihood * Math.pow(prior,LAMBDA) *
SCALE_FACTOR.
    */
    public String correctPhrase(String phrase)
    {
        if(phrase == null || phrase.length() == 0)
        {
            throw new IllegalArgumentException("phrase must be non-empty.");
        }
        
        // List of words (in sentence order), containing all suggestions and their channel model prob. per word.
        List<Map<String, Double>> sentenceMashups = new ArrayList();
        
        String[] words = phrase.split(" ");
        String finalSuggestion = "";
        
        long nrOfMashups = 1;
        // there are at most 2 erronous words of distance 1, this array is their indexes in the sentence.
        List<Integer> wrongwords = new ArrayList();
        
        for(int i = 0; i < words.length; i++) {
            String word = words[i];
            HashSet<String> candidate_words = getCandidateWords(word);
            Map<String, Double> candidate_words_cmp = new HashMap();
            
            if(!cr.inVocabulary(word)) {
                // Now we know for sure that the words connected to this wrong word are correct, so delete their suggestions.
                wrongwords.add(i);
                if(i > 1) {
                    // Replace previous set of suggested words for the previous word with prob. 1.0
                    Map<String, Double> prev_candidate_words_cmp = sentenceMashups.get(i-1);
                    nrOfMashups /= prev_candidate_words_cmp.size();
                    sentenceMashups.remove(prev_candidate_words_cmp);
                    prev_candidate_words_cmp = new HashMap();
                    prev_candidate_words_cmp.put(words[i-1], 1.0);
                    sentenceMashups.add(prev_candidate_words_cmp);
                }
            }else{
                // The current word is a candidate too if it exists in the vocabulary.
                candidate_words.add(word);
            }
            
            for(String candidate_word : candidate_words) {
                double replace_prob = calculateChannelModelProbability(candidate_word, word);
                candidate_words_cmp.put(candidate_word, replace_prob);
            }
            // nrOfMashups is a measurement for the state space explosion, that results from processing all possible candidates for every word.
            nrOfMashups *= candidate_words.size();
            
            sentenceMashups.add(candidate_words_cmp);
            
            if(!cr.inVocabulary(word) && i < words.length - 1) {
                // skip next word
                Map<String, Double> next_candidate_words_cmp = new HashMap();
                next_candidate_words_cmp.put(words[i+1], 1.0);
                sentenceMashups.add(next_candidate_words_cmp);
                i++;
            }
        }
        
        if(DEBUG) System.out.println("ERROR WORDS:"+Arrays.deepToString(wrongwords.toArray()));
        if(DEBUG) System.out.println("Mashups:"+nrOfMashups);
        printPhraseMatrix(phrase, sentenceMashups);
        
                
        // Generate all possible sentences
        List<Sentence> possibleSentences = new ArrayList();
                
        if(wrongwords.size() == 2) {
            // 2 erronous words, therefore all other words must be correct
            List<List<String>> lssb = new ArrayList();
           
            List<String> default_sentence = new ArrayList();
            for(int i = 0; i < words.length; i++) {
                default_sentence.add(words[i]);
            }
            lssb.add(default_sentence);
            
            int w1_index = wrongwords.get(0);
            int w2_index = wrongwords.get(1);
            
            for(String sugg_word : sentenceMashups.get(w1_index).keySet()) {
                List sb_new = new ArrayList(default_sentence);
                sb_new.set(w1_index, sugg_word);

                for(String sugg_word_two : sentenceMashups.get(w2_index).keySet()) {
                    sb_new.set(w2_index, sugg_word_two);
                    lssb.add(sb_new);
                }
            }
                
            for(int j = 0; j < lssb.size(); j++) {
                List l = lssb.get(j);
                possibleSentences.add(new Sentence(to_sentence(l)));
            }
            
            // TODO: clean up inside this switch and then merge code of the full condition.
        } else if(wrongwords.size() == 1) {
            // at most 2 wrong words at the same time, so this one and for every word add its suggestions
            // So take the wrong sentence, make many suggested sentences with 1 word replaced for its suggestion
            List<List<String>> lssb = new ArrayList();
            lssb.add(new ArrayList());
            
            for(int i = 0; i < words.length; i++) {
                int lssb_size = lssb.size();
                
                if(wrongwords.contains(i)) {
                    // replace word i by every suggestion except this wrong word
                    for(int j = 0; j < lssb_size; j++) {
                        List sb = lssb.get(j);
                        lssb.remove(sb);
                        for(String sugg_word : sentenceMashups.get(i).keySet()) {
                            List sb_new = new ArrayList(sb);
                            sb_new.add(sugg_word);
                            lssb.add(sb_new);
                        }
                    }
                }else{
                    for(int j = 0; j < lssb_size; j++) {
                       lssb.get(j).add(words[i]);
                    }
                }
            }
            
            // For every word: For every lssb sequence: replace with suggestions(/same word)
            for(int i = 0; i < words.length; i++) {
                if(!wrongwords.contains(i)) {
                    for(int j = 0; j < lssb.size(); j++) {
                        List sb = lssb.get(j);
                        
                        for(String sugg_word : sentenceMashups.get(i).keySet()) {
                            List sb_new = new ArrayList(sb);
                            // replace word i with its suggestion (or the word itself)
                            sb_new.set(i, sugg_word);
                            possibleSentences.add(new Sentence(to_sentence(sb_new)));
                        }
                    }
                }
            }
            
        } else if(wrongwords.size() == 0) {
            // This is where it gets tricky, if two words have too much suggestions we are dead..
            // for all w1,w2:
            //  rest of sentence + cartesian product of w1 and w2
            List<List<String>> lssb = new ArrayList();
           
            List<String> default_sentence = new ArrayList();
            for(int i = 0; i < words.length; i++) {
                default_sentence.add(words[i]);
            }
            lssb.add(default_sentence);
            // k is index of wrong word 1 (replace with all suggestions)
            // i is index of wrong word 2 (replace with all suggestions)
            // if(i==k) then there is just 1 wrong word (replace wi or wk with all suggestions)
            for(int k = 0; k < words.length/2; k++) { 
                for(int i = 1+words.length/2; i < words.length; i++) {

                    // replace word i by every suggestion except this wrong word
                    for(String sugg_word : sentenceMashups.get(i).keySet()) {
                        List sb_new = new ArrayList(default_sentence);
                        sb_new.set(i, sugg_word);
                        
                        if(i == k) {
                            lssb.add(sb_new);
                        }else{
                            for(String sugg_word_two : sentenceMashups.get(k).keySet()) {
                                sb_new.set(k, sugg_word_two);
                                lssb.add(sb_new);
                            }
                        }
                    }
                }

                for(int j = 0; j < lssb.size(); j++) {
                    List l = lssb.get(j);
                    possibleSentences.add(new Sentence(to_sentence(l)));
                }
                 
            }
        } else {
            System.err.println("More than 2 words are NOT in the vocabulary!");
            return "ERROR: false input. More than 2 words are NOT in the vocabulary!";
        }
        
        /**
         * We analyze every probable combination of words, within this priority queue.
         */
        PriorityQueue<Sentence> resultingSentences = new PriorityQueue(possibleSentences.size(), new SentenceComparator());
        
        if(DEBUG)System.out.println("Possible sentences: "+possibleSentences.size());
        // We have now calculated all possible sentences, one of which is correct. Now find it.
        for(Sentence s : possibleSentences) {            
            // Calculate probability score of a sentence
            double c_cmp_score = calculateSentenceScore(s.getStr(), phrase);
            s.setValue(c_cmp_score);
        }
        
        // Pick the sentence with highest probability score
        resultingSentences.addAll(possibleSentences);
        
        // Assume there are resulting sentences even when no word has suggestions
        Sentence finalSentence = resultingSentences.peek();
        finalSuggestion = finalSentence.getStr();
        
        for(int i = 0; i < Math.min(20, resultingSentences.size()); i++) {
            Sentence s = resultingSentences.poll();
            if(DEBUG) System.out.println(s.getValue()+"| "+s.getStr());
            // If there exist more 'equal' values then do not edit the sentence!
//            if(i > 0 && finalSentence.getValue().doubleValue() == s.getValue()) {
//                finalSuggestion = phrase;
//            }
        }
        // If more suggestions have equal values, but different replacement words, then do not replace the differing words.
        
        return finalSuggestion.trim();
    }
    
    String to_sentence(List<String> words) {
        if(words == null) return "";
        StringBuilder sb = new StringBuilder();
        for(String word: words) {
            sb.append(word).append(" ");
        }
        return sb.toString();
    }          

    private double calculateSentenceScore(String bettersentence, String badsentence) {
        String words[] = bettersentence.split(" ");
        String old_words[] = badsentence.split(" ");
        
        // 1. find changed words (0, 1, 2)
        List<Integer> changedWords = new ArrayList();
        for(int i = 0; i < Math.min(words.length, old_words.length); i++) {
            if(words[i] == null ? old_words[i] != null : !words[i].equals(old_words[i])) {
                changedWords.add(i);
            }
        }
        
        double total_prob = 0.5;
        
        double LAMBDA = 1;
        double prev = 1.0;
        String prev_ngram = "";
        String prev_ngram_old = "";
        for(int i : changedWords) {
            String ngram = (i>0) ? words[i-1]+" "+words[i] : words[i];
            String ngram_old = (i>0) ? old_words[i-1]+" "+old_words[i] : old_words[i];
            double letters = (i>0) ? words[i-1].length()+words[i].length() : words[i].length();
            double letters_old = (i>0) ? old_words[i-1].length()+old_words[i].length() : old_words[i].length();
            
            double ngram_prob = cr.getSmoothedCount(ngram) -  cr.getSmoothedCount(ngram_old);
//            double ngram_prob = cr.getSmoothedCount(ngram) / Math.log(Math.max(1.5, letters)) - cr.getSmoothedCount(ngram_old) / Math.log(Math.max(1.5, letters_old));
            total_prob += ngram_prob;
            prev_ngram = ngram;
            prev_ngram_old = ngram_old;
        }
        
        double ngram_sum = 0.0;
        for(int i = 0; i < words.length; i++) {
            String ngram = (i>0) ? words[i-1]+" "+words[i] : words[i];
            double ngram_prob = cr.getSmoothedCount(ngram);
            ngram_sum += ngram_prob;
        }
//        System.out.println(ngram_sum);
        return total_prob + 0.8 * ngram_sum;
//            String word = words[i];
//            String ngram = (i>0) ? words[i-1]+" "+word : word;
//            double ngram_prob = cr.getSmoothedCount(ngram);
//            HashSet<String> candidate_words = getCandidateWords(word);
//            
//            double maximum_replace_score = ngram_prob;
//            String maximum_candidate_word = word;
//            
//            for(String candidate_word : candidate_words) {
//                double replace_prob = calculateChannelModelProbability(candidate_word, word);
//                String ngram_candidate = (i>0) ? words[i-1]+" "+candidate_word : candidate_word;
//                double ngram_candidate_prob = cr.getSmoothedCount(ngram_candidate);
//                
//                if(ngram_candidate_prob > maximum_replace_score) {
//                    maximum_candidate_word = candidate_word;
//                    maximum_replace_score = ngram_candidate_prob;
//                }
//                System.out.println("candidate for "+word + " -("+ngram_candidate_prob+":"+replace_prob+")-> " +candidate_word);
//            }
    }
    
    /*
    The method calculateChannel is meant to calculate the conditional
probability of a presumably incorrect word given a
correction. You need to decide whether a candidate suggestion
for an aledgedly incorrect word is a deletion, insertion,
substitution or a transposition, and what is the likelihood for
this to occur based on the values in the confusion matrix (for
which code is provided at the end of the method).
    */
    // P(presumably incorrect word | correction) = P(presumably incorrect word AND correction) / P(correction)
    public double calculateChannelModelProbability(String suggested, String incorrect) 
    {
         /** CODE TO BE ADDED **/
        
        // Decide whether a candidate suggestion for an incorrect word is a 
        //  deletion, insertion, substitution, transposition 
        
        // return likelihood for this to occur based on the values in the confusion matrix
        // TODO: Use cmr.getConfusionCount(String error, String correct) 
        
        // Calculate score for presumably incorect word
        int i = 0;
        while(i < Math.min(suggested.length()-1, incorrect.length()-1)) {
            if(suggested.charAt(i) != incorrect.charAt(i)) break;
            i++;
        }
        
        String correct_letters, incorrect_letters;
        double confcount = 0;
        
        // TODO add length checks
        
        // suggested has deleted 1 letter at i
        if(i+1 < incorrect.length() && suggested.charAt(i) == incorrect.charAt(i+1)){
            correct_letters = String.valueOf(suggested.charAt(i));
            incorrect_letters = incorrect.substring(i, i + 2);
            
        // suggested has inserted 1 letter at i
        }else if(i+1 < suggested.length() && suggested.charAt(i+1) == incorrect.charAt(i)){
            correct_letters = suggested.substring(i, i + 2);
            incorrect_letters = String.valueOf(incorrect.charAt(i));
            
        // suggested has 1 letter randomly changed, or transpositioned with next letter
        }else if(i+1 < incorrect.length() && i+2 < suggested.length() && suggested.charAt(i+1) == incorrect.charAt(i) 
                && suggested.charAt(i) == incorrect.charAt(i+1)) {
            correct_letters = suggested.substring(i, i + 2);
            incorrect_letters = incorrect.substring(i, i + 2);
        }else {
            correct_letters = String.valueOf(suggested.charAt(i));
            incorrect_letters = String.valueOf(incorrect.charAt(i));
        }
        
        confcount = cmr.getConfusionCount(incorrect_letters, correct_letters);
        if(confcount == 0) {
            confcount = 41; // average
//            System.out.println(incorrect_letters +"|"+ correct_letters);
        }
        
//        cmr.getConfusionCount(String error, String correct) 
        
        
        return confcount/917.0;
    }
    
    /*
    You may want to tune the constants NO_ERROR and LAMBDA
to improve the reach of your program.
    */
         
      
    // DONE
    // Collect all words from the vocabulary that have edit-distance 1 to a word. 
    public HashSet<String> getCandidateWords(String word)
    {
        HashSet<String> ListOfWords = new HashSet<>();
        
        
        // Mess up the word in all possible ways, 
        //  such that the call below checks which of the modified versions is in the vocabulary?
        int wlen = word.length();
        StringBuilder sb, sb2;
        
        // Insertion before first letter
        for(int k = 0; k < ALPHABET.length; k++) {
            sb = new StringBuilder();
            // insert random letter
            sb.append(ALPHABET[k]);
            for(int j = 0; j < wlen; j++) {
                // append normal letter
                sb.append(word.charAt(j));
            }
            ListOfWords.add(sb.toString());
        }
        
        for(int letter_index = 0; letter_index < wlen; letter_index++) {
            // deletion
            sb = new StringBuilder();
            for(int j = 0; j < wlen; j++) {
                if(j != letter_index) sb.append(word.charAt(j));
            }
            ListOfWords.add(sb.toString());
            
            // transposition (swap with next letter)
            sb = new StringBuilder();
            for(int j = 0; j < wlen-1; j++) {
                if(j != letter_index) {
                    // append normal letter
                    sb.append(word.charAt(j));
                }else{
                    // insert random letter
                    sb.append(word.charAt(j+1));
                    sb.append(word.charAt(j));
                    j++; // skip one cycle since we add 2 letters at once.
                }
            }
            if(sb.length() < wlen) {
                sb.append(word.charAt(wlen-1));
            }
            ListOfWords.add(sb.toString());
            
            // substitution and insertion
            for(int k = 0; k < ALPHABET.length; k++) {
                sb = new StringBuilder();
                sb2 = new StringBuilder();
                for(int j = 0; j < wlen; j++) {
                    if(j != letter_index) {
                        // append normal letter
                        sb.append(word.charAt(j));
                        
                        sb2.append(word.charAt(j));
                    }else{
                        // substitute with random letter
                        sb.append(ALPHABET[k]);
                        
                        // insert random letter
                        sb2.append(word.charAt(j));
                        sb2.append(ALPHABET[k]);
                    }
                }
                ListOfWords.add(sb.toString());
                ListOfWords.add(sb2.toString());
            }
        }
        
        // This call returns only words which are in the vocabulary
        return cr.inVocabulary(ListOfWords);
    }
}