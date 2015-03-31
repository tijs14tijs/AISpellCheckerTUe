import java.util.Comparator;

/**
 *
 * @author Tijs
 */
public class SentenceComparator implements Comparator<Sentence>
{
    @Override
    public int compare(Sentence o1, Sentence o2) {
        return o2.getValue().compareTo(o1.getValue());
    }
}
