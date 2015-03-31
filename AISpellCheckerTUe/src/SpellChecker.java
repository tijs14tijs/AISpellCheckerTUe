import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.Set;


public class SpellChecker {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) 
    {
        boolean inPeach = false; // set this to true if you submit to peach!!!
        
        try {
            CorpusReader cr = new CorpusReader();
            ConfusionMatrixReader cmr = new ConfusionMatrixReader();
            SpellCorrector sc = new SpellCorrector(cr, cmr);
            if (inPeach) {
                peachTest(sc);
            } else {
//                testCMProbability(sc);
                nonPeachTest(sc);
            }
        } catch (Exception ex) {
            System.out.println(ex);
            ex.printStackTrace();
        }
    }
    
    static void nonPeachTest(SpellCorrector sc) throws IOException { 
            String[] sentences = {};
            FileInputStream fis;
            fis = new FileInputStream("test-sentences.txt");
            BufferedReader in = new BufferedReader(new InputStreamReader(fis));

            int correct = 0, tested = 0;
            
            while (in.ready()) {
                String trimmedline = in.readLine().trim();
                if(trimmedline.startsWith("#")) continue;
                String phrase, projectedAnswer;
                if(trimmedline.contains("=")){
                    phrase = trimmedline.split("=")[0];
                    projectedAnswer = trimmedline.split("=")[1];
                    String result=sc.correctPhrase(phrase);
                    if(!result.equals(projectedAnswer)) {
                        System.out.println("Input : " + phrase);
                        System.out.println("Answer: " +result);
                        System.out.println();
                    }else{
                        correct++;
                        System.out.println(correct+"/"+tested+": "+phrase+"\t->\t"+result);
                    }
                    tested++;
                }else{
                    phrase = trimmedline;
                    System.out.println("Input : " + phrase);
                    String result=sc.correctPhrase(phrase);
                    System.out.println("Answer: " +result);
                    System.out.println();
                }
            }
            
            for(String s0: sentences) {
                System.out.println("Input : " + s0);
                String result=sc.correctPhrase(s0);
                System.out.println("Answer: " +result);
                System.out.println();
            }
    }
    
    // Method to test the channel model probability function
    static void testCMProbability(SpellCorrector sc) {
        while(true) {
            Scanner input = new Scanner(System.in);
            String w1 = input.nextLine();
            
            if("exit".equalsIgnoreCase(w1)) break;
            Set<String> candidates = sc.getCandidateWords(w1);
            PriorityQueue<Sentence> pq = new PriorityQueue(candidates.size(), new SentenceComparator());
            
            for(String candidate : candidates) {
                double val = sc.calculateChannelModelProbability(candidate, w1);
                pq.add(new Sentence(val, candidate));
            }
            while(!pq.isEmpty()) {
                Sentence s = pq.poll();
                System.out.println("P("+s.getStr()+"|" +w1+")=" +s.getValue());
            }
        }
    }
    
    static void peachTest(SpellCorrector sc) throws IOException {
            Scanner input = new Scanner(System.in);
            
            String sentence = input.nextLine();
            System.out.println("Answer: " + sc.correctPhrase(sentence));  
    } 
}