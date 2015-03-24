import java.util.HashSet;

public class SpellCorrector {
    final private CorpusReader cr;
    final private ConfusionMatrixReader cmr;
    
    final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz'".toCharArray();
    
    
    public SpellCorrector(CorpusReader cr, ConfusionMatrixReader cmr) 
    {
        this.cr = cr;
        this.cmr = cmr;
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
            
        String[] words = phrase.split(" ");
        String finalSuggestion = "";
        
        // Calculate if each word is correct, or if a replacement is more likely
        for(int i = 0; i < words.length; i++) {
            String word = words[i];
            String ngram = (i>0) ? words[i-1]+" "+word : word;
            double ngram_prob = cr.getSmoothedCount(ngram);
            HashSet<String> candidate_words = getCandidateWords(word);
            
            double maximum_replace_score = ngram_prob;
            String maximum_candidate_word = word;
            
            for(String candidate_word : candidate_words) {
                double replace_prob = calculateChannelModelProbability(candidate_word, word);
                String ngram_candidate = (i>0) ? words[i-1]+" "+candidate_word : candidate_word;
                double ngram_candidate_prob = cr.getSmoothedCount(ngram_candidate);
                
                if(ngram_candidate_prob * replace_prob > maximum_replace_score) {
                    maximum_candidate_word = candidate_word;
                    maximum_replace_score = ngram_candidate_prob * replace_prob;
                }
                System.out.println("candidate for "+word + " -("+ngram_candidate_prob+":"+replace_prob+")-> " +candidate_word);
            }
            
//            if(maximum_replace_score > 0.5) {
//                System.out.println("replace "+word + " -("+maximum_replace_score+")-> " +maximum_candidate_word);
//            }
        }
        
        return finalSuggestion.trim();
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
        }else if(i+1 < incorrect.length() && i+2 < suggested.length() && suggested.charAt(i+1) == incorrect.charAt(i) && suggested.charAt(i) == incorrect.charAt(i+1)) {
            correct_letters = suggested.substring(i, i + 2);
            incorrect_letters = incorrect.substring(i, i + 2);
        }else {
            correct_letters = String.valueOf(suggested.charAt(i));
            incorrect_letters = String.valueOf(incorrect.charAt(i));
        }
        
        confcount = cmr.getConfusionCount(incorrect_letters, correct_letters);
        if(confcount == 0) {
            confcount = 41; // average
            System.out.println(incorrect_letters +"|"+ correct_letters);
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