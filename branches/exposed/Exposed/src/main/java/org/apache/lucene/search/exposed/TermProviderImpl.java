package org.apache.lucene.search.exposed;

import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.packed.GrowingMutable;
import org.apache.lucene.util.packed.PackedInts;

import java.io.IOException;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Default implementation of some methods from TermProvider.
 */
public abstract class TermProviderImpl implements TermProvider {
  protected boolean cacheTables;
  private PackedInts.Mutable docToSingle = null;

  private final IndexReader reader;
  private int docIDBase; // Changed on reopen
  private final Comparator<BytesRef> comparator;
  private final String comparatorID;
  private final String designation;

  /**
   * @param reader the underlying reader for this provider.
   * @param docIDBase the base to add to all docIDs returned from this provider.
   * @param comparator the comparator used for sorting. If this is null, the
   * natural BytesRef-order is used.
   * @param comparatorID an ID for the comparator. If this is natural order,
   * this should be {@link ExposedRequest#LUCENE_ORDER}.
   * @param designation used for feedback and debugging. No formal requirements.
   * @param cacheTables if true, tables such as orderedOrdinals, docID2indirect
   * and similar should be cached for re-requests after generation.
   */
  protected TermProviderImpl(
      IndexReader reader, int docIDBase,
      Comparator<BytesRef> comparator, String comparatorID,
      String designation, boolean cacheTables) {
    this.reader = reader;
    this.docIDBase = docIDBase;
    this.comparator = comparator;
    this.comparatorID = comparatorID;
    this.cacheTables = cacheTables;
    this.designation = designation;
  }

  public synchronized PackedInts.Reader getDocToSingleIndirect()
                                                            throws IOException {
    if (docToSingle != null) {
      return docToSingle;
    }
    if (getMaxDoc() > Integer.MAX_VALUE) {
      throw new UnsupportedOperationException(
          "Unable to handle more than Integer.MAX_VALUE documents. " +
              "Got macDocs " + getMaxDoc());
    }
    // TODO: getMaxDoc() is seriously wonky for GroupTermProvider. What breaks?
    long sortTime;
    // TODO: Check why it is extremely slow to start with low maxValue
    PackedInts.Mutable docToSingle = new GrowingMutable(
        0, (int)getMaxDoc(), -1, getOrdinalTermCount(),
        ExposedSettings.priority == ExposedSettings.PRIORITY.speed);
    try {
      Iterator<ExposedTuple> ei = getIterator(true);
      sortTime = System.currentTimeMillis();
      while (ei.hasNext()) {
        ExposedTuple tuple = ei.next();
        if (tuple.docIDs != null) {
/*          int read;
          final DocsEnum.BulkReadResult bulk = tuple.docIDs.getBulkResult();
          final IntsRef ints = bulk.docs;
          while ((read = tuple.docIDs.read()) > 0) {
            final int to = read + ints.offset;
            for (int i = ints.offset ; i < to ; i++) {
            docToSingle.set((int)(ints.ints[i] + tuple.docIDBase),
                tuple.indirect);
            }
          }
  */
          int doc;
          // TODO: Test if bulk reading (which includes freqs) is faster
          while ((doc = tuple.docIDs.nextDoc()) != DocsEnum.NO_MORE_DOCS) {
            docToSingle.set((int)(doc + tuple.docIDBase), tuple.indirect);
          }
        }
/*        if (tuple.indirect << 60 == 0) {
          System.out.print(".");
        }*/
      }
    } catch (IOException e) {
      throw new RuntimeException("Unable to create doc to indirect map", e);
    }
    sortTime = System.currentTimeMillis() - sortTime;
    if (ExposedSettings.debug) {
      System.out.println(this.getClass().getSimpleName() + " merge-mapped "
          + (getMaxDoc()-1) + " docs to single terms in " + sortTime + " ms: " +
          (sortTime == 0 ? "N/A" : docToSingle.size() / sortTime) + " docs/ms");
    }
    if (cacheTables) {
      this.docToSingle = docToSingle;
    }
    return docToSingle;
  }

  public String getDesignation() {
    return designation;
  }

  public Comparator<BytesRef> getComparator() {
    return comparator;
  }

  public String getComparatorID() {
    return comparatorID;
  }

  public IndexReader getReader() {
    return reader;
  }

  public void setDocIDBase(int base) {
    docIDBase = base;
  }

  public int getDocIDBase() {
    return docIDBase;
  }

  public int getNearestTermIndirect(BytesRef key) throws IOException {
    return getNearestTermIndirect(key, 0, (int)getUniqueTermCount());
  }
  public int getNearestTermIndirect(
      final BytesRef key, int startTermPos, int endTermPos) throws IOException {
    if (getComparator() != null) {
      return getNearestWithComparator(key, startTermPos, endTermPos);
    }
    int low = startTermPos;
    int high = endTermPos-1;
    while (low <= high) {
      int middle = low + high >>> 1;
      BytesRef midVal = getOrderedTerm(middle);
      int compValue = midVal.compareTo(key);
      if (compValue < 0) {
        low = middle + 1;
      } else if (compValue > 0) {
        high = middle - 1;
      } else {
        return middle;
      }
    }
    return low;
  }

  private int getNearestWithComparator(
      final BytesRef key, int startTermPos, int endTermPos) throws IOException {
    final Comparator<BytesRef> comparator = getComparator();
    int low = startTermPos;
    int high = endTermPos-1;
    while (low <= high) {
      int middle = low + high >>> 1;
      BytesRef midVal = getOrderedTerm(middle);
      int compValue = comparator.compare(midVal, key);
      if (compValue < 0) {
        low = middle + 1;
      } else if (compValue > 0) {
        high = middle - 1;
      } else {
        return middle;
      }
    }
    return low;
  }

  protected String packedSize(PackedInts.Reader packed) {
    long bytes = packed.size() * packed.getBitsPerValue() / 8;
    if (bytes > 1048576) {
      return bytes / 1048576 + " MB";
    }
    if (bytes > 1024) {
      return bytes / 1024 + " KB";
    }
    return bytes + " bytes";
  }

  @Override
  public String toString() {
    if (docToSingle == null) {
      return "TermProviderImpl(" + getDesignation()
          + ", no docToSingle cached)";
    }
    return "TermProviderImpl(" + getDesignation() + ", docToSingle.length=" 
        + docToSingle.size() + " mem=" + packedSize(docToSingle) + ")";
  }

  public void transitiveReleaseCaches(int level, boolean keepRoot) {
    if (keepRoot && level == 0) {
      return;
    }
    docToSingle = null;
  }
}