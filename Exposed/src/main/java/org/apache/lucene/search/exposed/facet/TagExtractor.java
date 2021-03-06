package org.apache.lucene.search.exposed.facet;

import org.apache.lucene.search.exposed.ExposedPriorityQueue;
import org.apache.lucene.search.exposed.TermProvider;
import org.apache.lucene.search.exposed.compare.ComparatorFactory;
import org.apache.lucene.search.exposed.compare.NamedComparator;
import org.apache.lucene.search.exposed.facet.request.FacetRequestGroup;
import org.apache.lucene.search.exposed.facet.request.SubtagsConstraints;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.PriorityQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper class for extracting tags for a faceting structure.
 */
public class TagExtractor {
  final private FacetRequestGroup requestGroup;
  final private Pattern splitPattern; // Defined if hierarchical
  final private FacetMap map;

  public TagExtractor(FacetRequestGroup requestGroup, FacetMap map) {
    this.requestGroup = requestGroup;
    splitPattern = requestGroup.isHierarchical() ? Pattern.compile(requestGroup.getDelimiter()) : null;
    this.map = map; 
  }

  public FacetResponse.Group extract(int groupID, TagCollector tagCollector, int startPos, int endPos) throws IOException {

    if (requestGroup.isHierarchical()) {
      TermProvider provider = map.getProviders().get(groupID);
      if (!(provider instanceof HierarchicalTermProvider)) {
        throw new IllegalStateException(
            "Internal inconsistency: The provider for " + requestGroup.getGroup().getName()
            + " should be hierarchical but is " + provider.getClass());
      }
      int delta = -map.getIndirectStarts()[groupID];
      return new FacetResponse.Group(requestGroup, extractHierarchical(
          requestGroup.getDeeperLevel(), 1, 
          (HierarchicalTermProvider)provider, delta, tagCollector, startPos, endPos));
    }

    NamedComparator.ORDER order = requestGroup.getOrder();
    if (NamedComparator.ORDER.count == order) {
      return extractCountResult(requestGroup, tagCollector, startPos, endPos);
    } else if (NamedComparator.ORDER.index == order || NamedComparator.ORDER.locale == order) {
      return extractOrderResult(groupID, tagCollector, startPos, endPos);
    }
    throw new UnsupportedOperationException("The order '" + order + "' is unknown");
  }

  private FacetResponse.TagCollection extractHierarchical(
      final SubtagsConstraints constraints,
      final int currentLevel,
      final HierarchicalTermProvider provider, final int delta,
      final TagCollector tagCollector, final int startPos, final int endPos) throws IOException {
    if (currentLevel > requestGroup.getLevels()) {
      return null; // Stop descending
    }
    // TODO: Support count, offset, path etc.
    switch( constraints.getSubtagsOrder()) {
      case base: return extractHierarchicalOrder(
          constraints, currentLevel, provider, delta, tagCollector, startPos, endPos);
     case count: return extractHierarchicalCount(
         constraints, currentLevel, provider, delta, tagCollector, startPos, endPos);
      default: throw new IllegalArgumentException(
          "The order '" + constraints.getSubtagsOrder() + "' is unknown");
    }
  }

  private class HCElement {
    final int tagStartPos;
    final int tagEndPos;
    final int count;
    final int totalCount;

    private HCElement(int tagStartPos, int tagEndPos, int count, int totalCount) {
      this.tagStartPos = tagStartPos;
      this.tagEndPos = tagEndPos;
      this.count = count;
      this.totalCount = totalCount;
    }
  }

  private class HCEPQ extends PriorityQueue<HCElement> {
    private HCEPQ(int size) {
      super(size, false); // TODO: Consider prepopulating
    }

    @Override
    protected boolean lessThan(HCElement a, HCElement b) {
      return a.totalCount == b.totalCount ?
          a.tagStartPos > b.tagStartPos :
          a.totalCount < b.totalCount;
    }
  }

  private FacetResponse.TagCollection extractHierarchicalCount(
      final SubtagsConstraints constraints, final int level, final HierarchicalTermProvider provider, final int delta,
      final TagCollector tagCollector, final int startPos, final int endPos) throws IOException {
    HCEPQ pq = new HCEPQ(Math.min(constraints.getMaxTags(), endPos - startPos));
    long validTags = 0;
    long totalCount = 0;
    long count = 0;
    { // Find most popular tags
      final TagSumIterator tagIterator = new TagSumIterator(
          provider, constraints, tagCollector.getTagCounts(), startPos, endPos, level, delta);
      while (tagIterator.next()) {
        totalCount += tagIterator.getTotalCount();
        count += tagIterator.getCount();
        final HCElement current = new HCElement(
            tagIterator.tagStartPos, tagIterator.tagEndPos, tagIterator.count, tagIterator.totalCount);
        pq.insertWithOverflow(current); // TODO: Consider reusing objects
        validTags++;
      }
    }
    // Build result structure and
    FacetResponse.Tag[] tags = new FacetResponse.Tag[pq.size()];
    for (int i = pq.size()-1 ; i >= 0 ; i--) {
      HCElement element = pq.pop();
      String term = provider.getOrderedDisplayTerm(element.tagStartPos + delta).utf8ToString();
      FacetResponse.Tag tag = new FacetResponse.Tag(getLevelTerm(term, level), element.count, element.totalCount);
      tags[i] = tag;
      if (level < requestGroup.getLevels() &&
          !(provider.getLevel(element.tagStartPos) < level+1 && element.tagStartPos+1 == element.tagEndPos)) {
        tag.setSubTags(extractHierarchical(
            constraints.getDeeperLevel(), level+1, provider, delta,
            tagCollector, element.tagStartPos, element.tagEndPos));
      }
      // TODO: State totalcount in tags so they are not forgotten
    }
    FacetResponse.TagCollection collection = new FacetResponse.TagCollection();
    collection.setTags(Arrays.asList(tags));
    collection.setTotalCount(totalCount);
    collection.setTotalTags(validTags);
    collection.setCount(count);
    collection.setDefiner(constraints);
    collection.setPotentialTags(endPos-startPos);
    return collection;
  }

  private FacetResponse.TagCollection extractHierarchicalOrder(
      final SubtagsConstraints constraints,
      final int level,
      final HierarchicalTermProvider provider,
      final int delta, 
      final TagCollector tagCollector, final int startTermPos, final int endTermPos) throws IOException {
    final TagSumIterator tagIterator = new TagSumIterator(
        provider, constraints, tagCollector.getTagCounts(), startTermPos, endTermPos, level, delta);
    // TODO: Consider optimization by not doing totalTags, count and totalCount
    List<FacetResponse.Tag> tags = new ArrayList<FacetResponse.Tag>(Math.max(1, Math.min(constraints.getMaxTags(), 100000)));
    long validTags = 0;
    long totalCount = 0;
    long count = 0;
    while (tagIterator.next()) {
      totalCount += tagIterator.getTotalCount();
      count += tagIterator.getCount();
      if (validTags < constraints.getMaxTags()) {
        String term = provider.getOrderedDisplayTerm(tagIterator.tagStartPos + delta).utf8ToString();
        FacetResponse.Tag tag = new FacetResponse.Tag(
            getLevelTerm(term, level), tagIterator.getCount(), tagIterator.getTotalCount());
        tags.add(tag);
        if (level < requestGroup.getLevels() &&
            !(provider.getLevel(tagIterator.tagStartPos) < level+1 &&
            tagIterator.tagStartPos+1 == tagIterator.tagEndPos)) {
          tag.setSubTags(extractHierarchical(
              constraints.getDeeperLevel(), level+1, provider, delta,
              tagCollector, tagIterator.tagStartPos, tagIterator.tagEndPos));
        }
      }
      validTags++;
    }
    FacetResponse.TagCollection collection = new FacetResponse.TagCollection();
    collection.setTags(tags);
    collection.setTotalCount(totalCount);
    collection.setTotalTags(validTags);
    collection.setCount(count);
    collection.setDefiner(constraints);
    collection.setPotentialTags(endTermPos-startTermPos);
    return collection;
  }

  private String getLevelTerm(String term, int level) {
    try {
      return splitPattern.split(term)[level-1];
    } catch (ArrayIndexOutOfBoundsException e) {
      ArrayIndexOutOfBoundsException ex = new ArrayIndexOutOfBoundsException(
          "Unable to split '" + term + "' with delimiter '"
          + splitPattern.pattern() + "' into " + level + " parts or more");
      ex.initCause(e);
      throw ex;
    }
  }

  private FacetResponse.Group extractOrderResult(
      final int groupID,
      final TagCollector tagCollector, final int startTermPos, final int endTermPos) throws IOException {
    long extractionTime = System.currentTimeMillis();
    final int[] tagCounts = tagCollector.getTagCounts();
    // Locate prefix
    int origo = requestGroup.isReverse() ? endTermPos-1 : startTermPos;
    if (requestGroup.getPrefix() != null && !"".equals(requestGroup.getPrefix())) {
      int tpOrigo = map.getProviders().get(groupID).getNearestTermIndirect(new BytesRef(requestGroup.getPrefix()));
      origo = tpOrigo + map.getIndirectStarts()[groupID];
      //origo = origo < 0 ? (origo + 1) * -1 : origo; // Ignore missing match
    }

    final int reverseDir = requestGroup.isReverse() ? -1 : 1;
    // skip to offset (only valid)
    final int minCount = requestGroup.getMinCount();
    final int direction = (requestGroup.getOffset() < 0 ? -1 : 1) * reverseDir;
    int delta =  Math.abs(requestGroup.getOffset());
    while (delta > 0) {
      origo += direction;
      if (origo < startTermPos || origo >= endTermPos) {
        origo += delta-1;
        break;
      }
      if (tagCounts[origo] >= minCount) {
        delta--;
      }
    }
    // Collect valid IDs
    int collectedTags = 0;
    if (origo < startTermPos) {
      collectedTags = startTermPos - origo;
      origo = startTermPos;
    } else if (origo >= endTermPos) {
      collectedTags = origo - (endTermPos-1);
      origo = endTermPos-1;
    }
    List<FacetResponse.Tag> tags = new ArrayList<FacetResponse.Tag>(
        Math.max(0, Math.min(
            requestGroup.getMaxTags(), requestGroup.isReverse() ? origo - startTermPos : endTermPos-origo)));
    for (int termPos = origo ;
         ((requestGroup.isReverse() && termPos >= startTermPos) ||
          (!requestGroup.isReverse() && termPos < endTermPos))
         && collectedTags < requestGroup.getMaxTags() ;
         termPos += reverseDir) {
      if (tagCounts[termPos] >= minCount) {
        tags.add(new FacetResponse.Tag(map.getOrderedDisplayTerm(termPos).utf8ToString(), tagCollector.get(termPos)));
        collectedTags++;
      }
    }
    FacetResponse.Group responseGroup = new FacetResponse.Group(requestGroup, tags);
    extractionTime = System.currentTimeMillis() - extractionTime;
    responseGroup.setExtractionTime(extractionTime);
    responseGroup.setPotentialTags(endTermPos - startTermPos);

    return responseGroup;
  }

  private FacetResponse.Group extractCountResult(
      FacetRequestGroup requestGroup, final TagCollector tagCollector, 
      final int startTermPos, final int endTermPos) throws IOException {
    long extractionTime = System.currentTimeMillis();
    // Sort tag references by count
    final int maxSize = Math.min(endTermPos - startTermPos, requestGroup.getMaxTags());
    final int minCount = requestGroup.getMinCount();

    ExposedPriorityQueue pq = getCountQueue(tagCollector, requestGroup, maxSize);

    CountCollector countCollector = new CountCollector(pq);
    tagCollector.iterate(countCollector, startTermPos, endTermPos, minCount);
/**    for (int tagID = startTermPos ; tagID < endTermPos ; tagID++) {
      final int count = tagCounts[tagID];
      totalRefs += count;
      if (tagCollector.get(tagID) >= minCount) {
        pq.insertWithOverflow(tagID);
        totalValidTags++;
      }
    }
   */
    // Extract Tags
    FacetResponse.Tag[] tags = new FacetResponse.Tag[pq.size()];
    int pos = pq.size()-1;
    while (pq.size() > 0) {
      final int termIndex = pq.pop();
      tags[pos--] =  new FacetResponse.Tag(
          map.getOrderedDisplayTerm(termIndex).utf8ToString(), tagCollector.get(termIndex));
    }

    // Create response
    FacetResponse.Group responseGroup = new FacetResponse.Group(requestGroup, Arrays.asList(tags));
    extractionTime = System.currentTimeMillis() - extractionTime;
    responseGroup.setExtractionTime(extractionTime);
    responseGroup.setPotentialTags(endTermPos-startTermPos);
    responseGroup.setTotalReferences(countCollector.getTotalRefs());
    responseGroup.setValidTags(countCollector.getTotalValidTags());
    return responseGroup;
  }

  private ExposedPriorityQueue getCountQueue(
      final TagCollector tagCollector, FacetRequestGroup requestGroup, int maxSize) {
    if (tagCollector.usesTagCountArray()) {
      final int[] tagCounts = tagCollector.getTagCounts();
      return new ExposedPriorityQueue(
          requestGroup.isReverse() ?
          new ComparatorFactory.IndirectComparatorReverse(tagCounts) :
          new ComparatorFactory.IndirectComparator(tagCounts), maxSize);
    }
    final int direction = requestGroup.isReverse() ? -1 : 1;
    return new ExposedPriorityQueue(new ComparatorFactory.OrdinalComparator() {
      @Override
      public int compare(int tagID1, int tagID2) {
        final int diff = tagCollector.get(tagID1)-tagCollector.get(tagID2);
        return direction * (diff == 0 ? tagID2 - tagID1 : diff);
      }
    }, maxSize);
  }

  private final class CountCollector implements TagCollector.IteratorCallback {
    private long totalRefs = 0;
    private long totalValidTags = 0;
    private final ExposedPriorityQueue pq;

    private CountCollector(ExposedPriorityQueue pq) {
      this.pq = pq;
    }

    @Override
    public boolean call(int tagID, int count) {
      totalRefs += count;
      pq.insertWithOverflow(tagID);
      totalValidTags++;
      return true;
    }

    public long getTotalRefs() {
      return totalRefs;
    }

    public long getTotalValidTags() {
      return totalValidTags;
    }
  }
}
