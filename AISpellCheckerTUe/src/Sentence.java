/**
 *
 * @author Tijs
 */
public class Sentence {
    private String str;
    private Double value;

    public Sentence(double value, String str) {
        this.str = str;
        this.value = value;
    }
    
    public Sentence(String str) {
        this.str = str;
    }

    public String getStr() {
        return str;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }
    
    
}
