package baritone.cache;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Count usable candidates, not rejected hits, against a bounded chunk-search quota. */
public final class SearchCandidates {
    private SearchCandidates() {}
    public static <T> List<T> nearest(Stream<T> candidates,Predicate<T> acceptable,java.util.Comparator<T> distance,int maximum) {
        return take(candidates.filter(acceptable).sorted(distance),value->true,maximum);
    }
    public static <T> List<T> take(Stream<T> candidates,Predicate<T> acceptable,int maximum) {
        Stream<T> eligible=candidates.filter(acceptable);
        if(maximum>=0)eligible=eligible.limit(maximum);
        return eligible.toList();
    }
}
