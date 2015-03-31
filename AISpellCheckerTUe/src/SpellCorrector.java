
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class SpellCorrector {

    final private CorpusReader cr;
    final private ConfusionMatrixReader cmr;
    final private boolean DEBUG = false;
    final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz'".toCharArray();

    public SpellCorrector(CorpusReader cr, ConfusionMatrixReader cmr) {
        this.cr = cr;
        this.cmr = cmr;
    }

    private void printPhraseMatrix(String sentence, List<Set<String>> sentenceMashups) {
        if (!DEBUG) {
            return;
        }
        System.out.println(sentence);
        System.out.println("-----------------------------------------------------------------------------------");

        for (int j = 0; j < sentenceMashups.size(); j++) {
            Set<String> wordmatrix = sentenceMashups.get(j);
            Object wordsuggestions[] = wordmatrix.toArray();
            for (int i = 0; i < wordsuggestions.length; i++) {
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
    public String correctPhrase(String phrase) {
        try {
            if (phrase == null || phrase.length() == 0) {
                throw new IllegalArgumentException("phrase must be non-empty.");
            }

            // List of words (in sentence order), containing all suggestions and their channel model prob. per word.
            List<Set<String>> sentenceWordSuggestions = new ArrayList();

            String[] words = phrase.split(" ");
            String finalSuggestion = "";

            // there are at most 2 erronous words of distance 1, this array is their indexes in the sentence.
            List<Integer> wrongwords = new ArrayList();

            for (int i = 0; i < words.length; i++) {
                String word = words[i];
                HashSet<String> candidate_words = getCandidateWords(word);

                if (!cr.inVocabulary(word)) {
                    // Now we know for sure that the words connected to this wrong word are correct, so delete their suggestions.
                    wrongwords.add(i);
                    if (i > 1) {
                        // Replace previous set of suggested words for the previous word with prob. 1.0
                        Set<String> prev_candidate_words_cmp = sentenceWordSuggestions.get(i - 1);
                        sentenceWordSuggestions.remove(prev_candidate_words_cmp);
                        prev_candidate_words_cmp = new HashSet();
                        prev_candidate_words_cmp.add(words[i - 1]);
                        sentenceWordSuggestions.add(prev_candidate_words_cmp);
                    }
                } else {
                    // The current word is a candidate too if it exists in the vocabulary.
                    candidate_words.add(word);
                }

                sentenceWordSuggestions.add(candidate_words);

                if (!cr.inVocabulary(word) && i < words.length - 1) {
                    // skip next word
                    Set<String> next_candidate_words_cmp = new HashSet();
                    next_candidate_words_cmp.add(words[i + 1]);
                    sentenceWordSuggestions.add(next_candidate_words_cmp);
                    i++;
                }
            }

            if (DEBUG) {
                System.out.println("ERROR WORDS:" + Arrays.deepToString(wrongwords.toArray()));
            }
            printPhraseMatrix(phrase, sentenceWordSuggestions);

            // Generate all possible sentences
            List<Sentence> possibleSentences = new ArrayList();
            // Default sentence (of maybe incorrect words)
            List<String> default_sentence = to_list(phrase);
            // Array of mashup sentences (as lists)
            List<List<String>> lssb = new ArrayList();

            if (wrongwords.size() == 2) {
            // 2 erronous words, therefore all other words must be correct

                int w1_index = wrongwords.get(0);
                int w2_index = wrongwords.get(1);

                for (String sugg_word : sentenceWordSuggestions.get(w1_index)) {
                    List sb_new = new ArrayList(default_sentence);
                    sb_new.set(w1_index, sugg_word);

                    for (String sugg_word_two : sentenceWordSuggestions.get(w2_index)) {
                        sb_new.set(w2_index, sugg_word_two);
                        lssb.add(sb_new);
                    }
                }

                for (int j = 0; j < lssb.size(); j++) {
                    List l = lssb.get(j);
                    possibleSentences.add(new Sentence(to_sentence(l)));
                }

                // TODO: clean up inside this switch and then merge code of the full condition.
            } else if (wrongwords.size() == 1) {
            // at most 2 wrong words at the same time, so this one and for every word add its suggestions
                // So take the wrong sentence, make many suggested sentences with 1 word replaced for its suggestion

                // Generate a sentence for any suggested replacement word
                int w1_index = wrongwords.get(0);
                for (String sugg_word : sentenceWordSuggestions.get(w1_index)) {
                    List mashup_sentence = new ArrayList(default_sentence);
                    mashup_sentence.set(w1_index, sugg_word);
                    lssb.add(mashup_sentence);
                    possibleSentences.add(new Sentence(to_sentence(mashup_sentence)));
                }

                // For any generated sentence: generate a new sentence where each word is replaced by a suggestion of it.
                int lssb_size = lssb.size();
                for (int i = 0; i < words.length; i++) {
                    // The neighbour words of a wrong word are always correct, so they have no suggestions
                    if (Math.abs(w1_index - i) > 1) {
                        // replace word i by every suggestion except this wrong word
                        for (int j = 0; j < lssb_size; j++) {
                            List mashup_sentence = lssb.get(j);
                            for (String sugg_word : sentenceWordSuggestions.get(i)) {
                                List mashup_sentence_new = new ArrayList(mashup_sentence);
                                mashup_sentence_new.set(i, sugg_word);
                                possibleSentences.add(new Sentence(to_sentence(mashup_sentence_new)));
                            }
                        }
                    }
                }

            } else if (wrongwords.size() == 0) {
            // This is where it gets tricky, if two words have too much suggestions we are dead..
                // for all w1,w2:
                //  rest of sentence + cartesian product of w1 and w2

                // Since the sentence contains no direct errors, it is a suggestion by itself too.
                lssb.add(default_sentence);

            // k is index of wrong word 1 (replace with all suggestions)
                // i is index of wrong word 2 (replace with all suggestions)
                // if(i==k) then there is just 1 wrong word (replace wi or wk with all suggestions)
                for (int k = 0; k < words.length / 2; k++) {
                    for (int i = 1 + words.length / 2; i < words.length; i++) {

                        // replace word i by every suggestion except this wrong word
                        for (String sugg_word : sentenceWordSuggestions.get(i)) {
                            List mashup_sentence = new ArrayList(default_sentence);
                            mashup_sentence.set(i, sugg_word);

                            for (String sugg_word_two : sentenceWordSuggestions.get(k)) {
                                mashup_sentence.set(k, sugg_word_two);
                                possibleSentences.add(new Sentence(to_sentence(mashup_sentence)));
                            }
                        }
                    }
                }
            } else {
                System.err.println("More than 2 words are NOT in the vocabulary!");
                return "ERROR: false input. More than 2 words are NOT in the vocabulary!";
            }

            // Check if we can continue, otherwise pretty exit
            if (possibleSentences.isEmpty()) {
                String undefwords = "";
                if (wrongwords.size() >= 1) {
                    undefwords = words[wrongwords.get(0)];
                }
                if (wrongwords.size() >= 2) {
                    undefwords += " or " + words[wrongwords.get(1)];
                }
                return "Word " + undefwords + " does not even approach a dictionary word >:|";
            }

            /**
             * We analyze every probable combination of words, within this priority queue.
             */
            PriorityQueue<Sentence> resultingSentences = new PriorityQueue(possibleSentences.size(), new SentenceComparator());

            if (DEBUG) {
                System.out.println("Possible sentences: " + possibleSentences.size());
            }
            // We have now calculated all possible sentences, one of which is correct. Now find it.
            for (Sentence s : possibleSentences) {
                // Calculate probability score of a sentence
                double c_cmp_score = calculateSentenceScore(s.getStr(), phrase);
                s.setValue(c_cmp_score);
            }

            // Pick the sentence with highest probability score
            resultingSentences.addAll(possibleSentences);

            // Assume there are resulting sentences even when no word has suggestions
            Sentence finalSentence = resultingSentences.peek();
            finalSuggestion = finalSentence.getStr();

            for (int i = 0; i < Math.min(5, resultingSentences.size()); i++) {
                Sentence s = resultingSentences.poll();
                if (DEBUG) {
                    System.out.println(s.getValue() + "| " + s.getStr());
                }
            }

            return finalSuggestion.trim();

        } catch (Exception e) {
            // Just make sure that if for some reason the previous fails, we do not die terribly.
            return "We could not create any possible resulting sentence :'(";
        }
    }

    // Small helper function to convert list of words to sentence.str
    String to_sentence(List<String> words) {
        if (words == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word).append(" ");
        }
        return sb.toString();
    }

    List<String> to_list(String sentence) {
        List<String> sentence_list = new ArrayList();
        String words[] = sentence.split(" ");
        for (int i = 0; i < words.length; i++) {
            sentence_list.add(words[i]);
        }
        return sentence_list;
    }

    /**
     * Calculate conditional probability of the suggested sentence given the bad sentence. This is where the magic or AI
     * reside.
     */
    private double calculateSentenceScore(String bettersentence, String badsentence) {
        String words[] = bettersentence.split(" ");
        String old_words[] = badsentence.split(" ");
        // 0.80061 8580454548-0.80061 6708438260
        double total_prob = 0.0;

        // 1. find changed words (0, 1, 2)
        List<Integer> changedWords = new ArrayList();
        for (int i = 0; i < Math.min(words.length, old_words.length); i++) {
            if (words[i] == null ? old_words[i] != null : !words[i].equals(old_words[i])) {
                changedWords.add(i);
            }
        }

        double LAMBDA = 1;
        double prev = 1.0;
        String prev_ngram = "";
        String prev_ngram_old = "";
        for (int i : changedWords) {
            String ngram = (i > 0) ? words[i - 1] + " " + words[i] : words[i];
            String ngram_fw = (i < words.length - 2) ? words[i] + " " + words[i + 1] : words[i];
            String ngram_old = (i > 0) ? old_words[i - 1] + " " + old_words[i] : old_words[i];
            String ngram_fw_old = (i < old_words.length - 2) ? old_words[i] + " " + old_words[i + 1] : old_words[i];

            // Stuff I tried to use but was not very useful
            double letters = (i > 0) ? words[i - 1].length() + words[i].length() : words[i].length();
            double letters_old = (i > 0) ? old_words[i - 1].length() + old_words[i].length() : old_words[i].length();
            double cm_prob = calculateChannelModelProbability(words[i], old_words[i]);

            double ngram_prob;
            // Check if the replacement is better with the word before and after it.
            if (i == 0) {
                ngram_prob = cr.getSmoothedCount(ngram_fw) - cr.getSmoothedCount(ngram_fw_old);
            } else if (i >= words.length - 2) {
                ngram_prob = cr.getSmoothedCount(ngram) - cr.getSmoothedCount(ngram_old);
            } else {
                ngram_prob = (cr.getSmoothedCount(ngram) * cr.getSmoothedCount(ngram_fw)) - (cr.getSmoothedCount(ngram_old) * cr.getSmoothedCount(ngram_fw_old));
            }
//            System.out.println("cm_prob:"+cm_prob+",smooth:"+cr.getSmoothedCount(ngram));
//            if(DEBUG) System.out.println("P("+words[i]+"|"+old_words[i]+")="+cm_prob);
//            double ngram_prob = cr.getSmoothedCount(ngram) / Math.log(Math.max(1.5, letters)) - cr.getSmoothedCount(ngram_old) / Math.log(Math.max(1.5, letters_old));
            total_prob += ngram_prob;
            prev_ngram = ngram;
            prev_ngram_old = ngram_old;
        }

        return total_prob;
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
    public double calculateChannelModelProbability(String suggested, String incorrect) {
        // If both words are equal then there is no channel model probability, so we return 0.5 
        if (suggested.equals(incorrect)) {
            return 1.0;
        }

        /**
         * CODE TO BE ADDED *
         */
        // Decide whether a candidate suggestion for an incorrect word is a 
        //  deletion, insertion, substitution, transposition 
        // return likelihood for this to occur based on the values in the confusion matrix
        // Check which characters have changed in the suggestion
        int i = 0;
        int index_suggest = 0, index_incorrect = 0;
        while (i < Math.max(suggested.length() - 1, incorrect.length() - 1)) {
            index_suggest = Math.min(i, suggested.length() - 1);
            index_incorrect = Math.min(i, incorrect.length() - 1);
            if (suggested.charAt(index_suggest) != incorrect.charAt(index_incorrect)) {
                break;
            }
            i++;
        }

        String correct_letters, incorrect_letters;

        // suggested has deleted 1 letter at i
        if (index_incorrect + 1 < incorrect.length() && suggested.charAt(index_suggest) == incorrect.charAt(index_incorrect + 1)) {
            correct_letters = String.valueOf(suggested.charAt(index_suggest));
            incorrect_letters = incorrect.substring(index_incorrect, index_incorrect + 2);

            // suggested has inserted 1 letter at i
        } else if (index_suggest + 1 < suggested.length() && suggested.charAt(index_suggest) == incorrect.charAt(index_incorrect)) {
            correct_letters = suggested.substring(index_suggest, index_suggest + 2);
            incorrect_letters = String.valueOf(incorrect.charAt(index_incorrect));

            // suggested has 1 letter randomly changed, or transpositioned with next letter
        } else if (index_incorrect + 1 < incorrect.length() && index_suggest + 2 < suggested.length() && suggested.charAt(index_suggest + 1) == incorrect.charAt(index_incorrect)
                && suggested.charAt(index_suggest) == incorrect.charAt(i + 1)) {
            correct_letters = suggested.substring(index_suggest, index_suggest + 2);
            incorrect_letters = incorrect.substring(index_incorrect, index_incorrect + 2);
        } else {
            correct_letters = String.valueOf(suggested.charAt(index_suggest));
            incorrect_letters = String.valueOf(incorrect.charAt(index_incorrect));
        }

        // Calculate probability that suggested is a good sugestion.
        double confcount = cmr.getConfusionCount(incorrect_letters, correct_letters);
        // Same idea as add-one smoothing, because we want to avoid probabilities of 0.
        if (confcount == 0) {
            confcount = 1;
        }

//        System.out.println(correct_letters + "-("+confcount+")>" + incorrect_letters);
        // Divide by a number slightly larger than the largest prob. count, so that our range is (0,1)
        return confcount / 920.0;
    }

    /*
     You may want to tune the constants NO_ERROR and LAMBDA
     to improve the reach of your program.
     */
    // Collect all words from the vocabulary that have exactly edit-distance 1 to a word. 
    public HashSet<String> getCandidateWords(String word) {
        HashSet<String> ListOfWords = new HashSet<>();

        // Mess up the word in all possible ways, 
        //  such that the call below checks which of the modified versions is in the vocabulary?
        int wlen = word.length();
        StringBuilder sb, sb2;

        // Insertion before first letter
        for (int k = 0; k < ALPHABET.length; k++) {
            sb = new StringBuilder();
            // insert random letter
            sb.append(ALPHABET[k]);
            for (int j = 0; j < wlen; j++) {
                // append normal letter
                sb.append(word.charAt(j));
            }
            ListOfWords.add(sb.toString());
        }

        for (int letter_index = 0; letter_index < wlen; letter_index++) {
            // deletion
            sb = new StringBuilder();
            for (int j = 0; j < wlen; j++) {
                if (j != letter_index) {
                    sb.append(word.charAt(j));
                }
            }
            ListOfWords.add(sb.toString());

            // transposition (swap with next letter)
            sb = new StringBuilder();
            for (int j = 0; j < wlen - 1; j++) {
                if (j != letter_index) {
                    // append normal letter
                    sb.append(word.charAt(j));
                } else {
                    // insert random letter
                    sb.append(word.charAt(j + 1));
                    sb.append(word.charAt(j));
                    j++; // skip one cycle since we add 2 letters at once.
                }
            }
            if (sb.length() < wlen) {
                sb.append(word.charAt(wlen - 1));
            }
            String sb_mashupword = sb.toString();
            if (!sb_mashupword.equals(word)) {
                ListOfWords.add(sb_mashupword);
            }

            // substitution and insertion
            for (int k = 0; k < ALPHABET.length; k++) {
                sb = new StringBuilder();
                sb2 = new StringBuilder();
                for (int j = 0; j < wlen; j++) {
                    if (j != letter_index) {
                        // append normal letter
                        sb.append(word.charAt(j));

                        sb2.append(word.charAt(j));
                    } else {
                        // substitute with random letter
                        sb.append(ALPHABET[k]);

                        // insert random letter
                        sb2.append(word.charAt(j));
                        sb2.append(ALPHABET[k]);
                    }
                }
                sb_mashupword = sb.toString();
                if (!sb_mashupword.equals(word)) {
                    ListOfWords.add(sb_mashupword);
                }
                ListOfWords.add(sb2.toString());
            }
        }

        // This call returns only words which are in the vocabulary
        return cr.inVocabulary(ListOfWords);
    }
}
