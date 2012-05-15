package org.apache.lucene.search.exposed;

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.packed.PackedInts;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Wrapper for TermProvider that provides a flexible cache for terms.
 * Warning: This cache does not work well for docIDs.
 * </p><p>
 * For random access, a plain setup with no read-ahead is suitable.
 * </p><p>
 * For straight iteration in ordinal order, a setup with maximum read-ahead
 * is preferable. This is uncommon.
 * </p><p>
 * For iteration in sorted ordinal order, where the ordinal order is fairly
 * aligned to unicode sorting order, a setup with some read-ahead works well.
 * This is a common case.
 * </p><p>
 * For merging chunks (See the SegmentReader.getSortedTermOrdinals), where
 * the ordinal order inside the chunks if fairly aligned to unicode sorting
 * order, a read-ahead works iff {@link #onlyReadAheadIfSpace} is true as this
 * prevents values from the beginning of other chunks from being flushed when
 * values from the current chunk is filled with read-ahead. This requires that
 * the merger removes processed values from the cache explicitly.
 * </p><p>
 * If the underlying order is used by calling {@link #getOrderedTerm(long)},
 * {@link #getOrderedField(long)} or {@link #getOrderedOrdinals()}, the
 * methods are delegated directly to the backing TermProvider. It is the
 * responsibility of the caller to endure that proper caching of the order
 * is done at the source level.
 */
public class CachedTermProvider extends CachedProvider<BytesRef>
                                                       implements TermProvider {
  private final TermProvider source;

  /**
   *
   * @param source       the backing term provider.
   * @param cacheSize    the maximum number of elements to hold in cache.
   * @param readAhead    the maximum number of lookups that can be performed
   *                     after a plain lookup.
   * @throws java.io.IOException if the cache could access the source.
   */
  public CachedTermProvider(
      TermProvider source, int cacheSize, int readAhead) throws IOException {
    super(cacheSize, readAhead, source.getOrdinalTermCount()-1);
    this.source = source;
  }

  @Override
  protected BytesRef lookup(final long index) throws IOException {
    return source.getTerm(index);
  }

  @Override
  public BytesRef getTerm(final long ordinal) throws IOException {
    return get(ordinal);
  }

  @Override
  public Iterator<ExposedTuple> getIterator(boolean collectDocIDs)
                                                            throws IOException {
    throw new UnsupportedOperationException(
        "The cache does not support the creation of iterators");
  }

  @Override
  public String getDesignation() {
    return "CachedTermProvider(" + source.getClass().getSimpleName() + "("
        + source.getDesignation() + "))";
  }

  public void transitiveReleaseCaches(int level, boolean keepRoot) {
    clear();
    source.transitiveReleaseCaches(level, keepRoot);
  }

  /* Straight delegations */

  public int getNearestTermIndirect(BytesRef key) throws IOException {
    return source.getNearestTermIndirect(key);
  }

  public int getNearestTermIndirect(BytesRef key, int startTermPos, int endTermPos) throws IOException {
    return source.getNearestTermIndirect(key, startTermPos, endTermPos);
  }

  public Comparator<BytesRef> getComparator() {
    return source.getComparator();
  }

  public String getComparatorID() {
    return source.getComparatorID();
  }

  public String getField(long ordinal) throws IOException {
    return source.getField(ordinal);
  }

  public String getOrderedField(long indirect) throws IOException {
    return source.getOrderedField(indirect);
  }

  public BytesRef getOrderedTerm(long indirect) throws IOException {
    return source.getOrderedTerm(indirect);
  }

  public long getUniqueTermCount() throws IOException {
    return source.getUniqueTermCount();
  }

  public long getOrdinalTermCount() throws IOException {
    return source.getOrdinalTermCount();
  }

  public long getMaxDoc() {
    return source.getMaxDoc();
  }

  public IndexReader getReader() {
    return source.getReader();
  }

  public DocsEnum getDocsEnum(long ordinal, DocsEnum reuse) throws IOException {
    return source.getDocsEnum(ordinal, reuse);
  }

  public PackedInts.Reader getOrderedOrdinals() throws IOException {
    return source.getOrderedOrdinals();
  }

  public PackedInts.Reader getDocToSingleIndirect() throws IOException {
    return source.getDocToSingleIndirect();
  }

  public int getReaderHash() {
    return source.getReaderHash();
  }

  public int getRecursiveHash() {
    return source.getRecursiveHash();
  }

  public int getDocIDBase() {
    return source.getDocIDBase();
  }

  public void setDocIDBase(int base) {
    source.setDocIDBase(base);
  }
}