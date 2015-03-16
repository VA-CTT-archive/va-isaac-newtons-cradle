package gov.vha.isaac.cradle.taxonomy;

import gov.vha.isaac.cradle.taxonomy.TypeStampTaxonomyRecords.TypeStampTaxonomyRecord;
import gov.vha.isaac.cradle.version.StampSequenceComputer;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.ochre.api.coordinate.TaxonomyCoordinate;
import org.apache.mahout.math.function.IntObjectProcedure;
import org.apache.mahout.math.list.IntArrayList;
import org.apache.mahout.math.map.OpenIntObjectHashMap;
import org.ihtsdo.otf.tcc.api.store.Ts;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * A {@code TaxonomyRecordUnpacked} is the value for a map where the key is the
 * concept sequence for a concept in the taxonomy, and the value is a map to
 * other concept sequences, and the associated stamps and taxonomy flags for
 * these other concept sequences. From the stamp value and the taxonomy flags,
 * all historic taxonomic associations (parent, child, stated, and inferred) can
 * be computed.
 *
 *
 * origin concept sequence [1 -> n] {destination concept sequence [1 -> n] stamp
 * + inferred + stated + parent + child}
 * <p>
 * <p>
 * Created by kec on 11/8/14.
 */
public class TaxonomyRecordUnpacked {

    /**
     * key = origin concept sequence; value = TypeStampTaxonomyRecords
     */
    private final OpenIntObjectHashMap<TypeStampTaxonomyRecords> conceptSequenceRecordMap = new OpenIntObjectHashMap<>(11);

    public TaxonomyRecordUnpacked() {
    }

    public TaxonomyRecordUnpacked(int[] recordArray) {
        if (recordArray != null) {
            int index = 0;
            while (index < recordArray.length) {
                int conceptSequence = recordArray[index] & TaxonomyRecordPrimitive.SEQUENCE_BIT_MASK;
                int length = recordArray[index] >>> 24;

                TypeStampTaxonomyRecords record = new TypeStampTaxonomyRecords(recordArray, index);
                index += length;
                conceptSequenceRecordMap.put(conceptSequence, record);
            }
        }
    }

    public boolean containsSequenceViaTypeWithFlags(int conceptSequence, int typeSequence, int flags) {
        if (conceptSequenceRecordMap.containsKey(conceptSequence)) {
            return conceptSequenceRecordMap.get(conceptSequence).containsStampOfTypeWithFlags(typeSequence, flags);
        }
        return false;
    }

    boolean containsActiveConceptSequenceViaType(int conceptSequence, int typeSequence, TaxonomyCoordinate tc) {
        StampSequenceComputer computer = StampSequenceComputer.getComputer(tc.getStampCoordinate());
        if (conceptSequenceRecordMap.containsKey(conceptSequence)) {
            return conceptSequenceRecordMap.get(conceptSequence).isActive(typeSequence, tc, computer);
        }
        return false;
    }

    public boolean containsActiveConceptSequenceViaType(int conceptSequence, int typeSequence, TaxonomyCoordinate tc, int flags) {
        StampSequenceComputer computer = StampSequenceComputer.getComputer(tc.getStampCoordinate());
        if (conceptSequenceRecordMap.containsKey(conceptSequence)) {
            return conceptSequenceRecordMap.get(conceptSequence).isActive(typeSequence, flags, computer);
        }
        return false;
    }

    boolean containsVisibleConceptSequenceViaType(int conceptSequence, int typeSequence, TaxonomyCoordinate tc) {
        StampSequenceComputer computer = StampSequenceComputer.getComputer(tc.getStampCoordinate());
        if (conceptSequenceRecordMap.containsKey(conceptSequence)) {
            return conceptSequenceRecordMap.get(conceptSequence).isVisible(typeSequence, tc, computer);
        }
        return false;
    }

    boolean containsVisibleConceptSequenceViaType(int conceptSequence, int typeSequence, TaxonomyCoordinate tc, int flags) {
        StampSequenceComputer computer = StampSequenceComputer.getComputer(tc.getStampCoordinate());
        if (conceptSequenceRecordMap.containsKey(conceptSequence)) {
            return conceptSequenceRecordMap.get(conceptSequence).isVisible(typeSequence, flags, computer);
        }
        return false;
    }

    public Optional<TypeStampTaxonomyRecords> getConceptSequenceStampRecords(int conceptSequence) {
        return Optional.ofNullable(conceptSequenceRecordMap.get(conceptSequence));
    }

    /**
     * @param typeSequence typeSequence to match, or Integer.MAX_VALUE if a
     * wildcard.
     * @param tc used to determine if a concept is active.
     * @return active concepts identified by their sequence value.
     */
    public IntStream getActiveConceptSequencesForType(int typeSequence, TaxonomyCoordinate tc) {
        int flags = TaxonomyFlags.getFlagsFromTaxonomyCoordinate(tc);
        StampSequenceComputer computer = StampSequenceComputer.getComputer(tc.getStampCoordinate());
        IntStream.Builder conceptSequenceIntStream = IntStream.builder();
        conceptSequenceRecordMap.forEachPair((int possibleParentSequence, TypeStampTaxonomyRecords stampRecords) -> {
            IntStream.Builder stampsForConceptIntStream = IntStream.builder();
            stampRecords.getTypeStampFlagStream().forEach((typeStampFlag) -> {
                TypeStampTaxonomyRecord record = new TypeStampTaxonomyRecord(typeStampFlag);
                if ((record.getTaxonomyFlags() & flags) == flags) {
                    if (typeSequence == Integer.MAX_VALUE) {
                        stampsForConceptIntStream.add(record.getStamp());
                    } else if (typeSequence == record.getTypeSequence()) {
                        stampsForConceptIntStream.add(record.getStamp());
                    }
                }
            });
            if (computer.isLatestActive(stampsForConceptIntStream.build())) {
                conceptSequenceIntStream.accept(possibleParentSequence);
            }
            return true;
        });
        return conceptSequenceIntStream.build();
    }

    public IntStream getVisibleConceptSequencesForType(int typeSequence, TaxonomyCoordinate tc) {
        final int flags = TaxonomyFlags.getFlagsFromTaxonomyCoordinate(tc);
        StampSequenceComputer computer = StampSequenceComputer.getComputer(tc.getStampCoordinate());
        IntStream.Builder conceptSequenceIntStream = IntStream.builder();
        conceptSequenceRecordMap.forEachPair((int possibleParentSequence, TypeStampTaxonomyRecords stampRecords) -> {
            stampRecords.getTypeStampFlagStream().forEach((typeStampFlag) -> {
                TypeStampTaxonomyRecord record = new TypeStampTaxonomyRecord(typeStampFlag);
                if ((record.getTaxonomyFlags() & flags) == flags) {
                    if (computer.onRoute(record.getStamp())) {
                        if (typeSequence == Integer.MAX_VALUE) {
                            conceptSequenceIntStream.accept(possibleParentSequence);
                        } else if (record.getTypeSequence() == typeSequence) {
                            conceptSequenceIntStream.accept(possibleParentSequence);
                        }
                    }
                }
            });
            return true;
        });
        return conceptSequenceIntStream.build();
    }

    public IntStream getParentConceptSequences() {

        IntStream.Builder conceptSequenceIntStream = IntStream.builder();
        conceptSequenceRecordMap.forEachPair((int possibleParentSequence, TypeStampTaxonomyRecords stampRecords) -> {
            stampRecords.getTypeStampFlagStream().forEach((typeStampFlag) -> {
                if ((typeStampFlag & TaxonomyRecordPrimitive.SEQUENCE_BIT_MASK) == IsaacMetadataAuxiliaryBinding.IS_A.getSequence()) {
                    conceptSequenceIntStream.accept(possibleParentSequence);
                }
            });
            return true;
        });
        return conceptSequenceIntStream.build();
    }

    public int conectionCount() {
        return conceptSequenceRecordMap.size();
    }

    public void addConceptSequenceStampRecords(int conceptSequence, TypeStampTaxonomyRecords newRecord) {
        if (conceptSequenceRecordMap.containsKey(conceptSequence)) {
            TypeStampTaxonomyRecords oldRecord = conceptSequenceRecordMap.get(conceptSequence);
            oldRecord.merge(newRecord);
        } else {
            conceptSequenceRecordMap.put(conceptSequence, newRecord);
        }
    }

    public void merge(TaxonomyRecordUnpacked newRecord) {
        newRecord.conceptSequenceRecordMap.forEachPair((int key, TypeStampTaxonomyRecords value) -> {
            if (conceptSequenceRecordMap.containsKey(key)) {
                conceptSequenceRecordMap.get(key).merge(value);
            } else {
                conceptSequenceRecordMap.put(key, value);
            }
            return true;
        });
    }

    public int[] pack() {
        PackConceptSequenceStampRecords packer = new PackConceptSequenceStampRecords();
        conceptSequenceRecordMap.forEachPair(packer);
        return packer.taxonomyRecordArray;
    }

    public int length() {
        int length = 0;
        length = conceptSequenceRecordMap.values().stream().map((record) -> record.length()).reduce(length, Integer::sum);
        return length;
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException("May change values, can't put in a tree or set");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TaxonomyRecordUnpacked other = (TaxonomyRecordUnpacked) obj;
        return Objects.equals(this.conceptSequenceRecordMap, other.conceptSequenceRecordMap);
    }

    @Override
    public String toString() {
        try {
            IntArrayList theKeys = conceptSequenceRecordMap.keys();
            theKeys.sort();

            StringBuilder buf = new StringBuilder();
            buf.append(" [");
            int maxIndex = theKeys.size() - 1;
            for (int i = 0; i <= maxIndex; i++) {
                int conceptSequence = theKeys.get(i);
                buf.append(Ts.get().getConcept(conceptSequence));
                buf.append(" |");
                buf.append(conceptSequence);
                buf.append("|->");
                TypeStampTaxonomyRecords value = conceptSequenceRecordMap.get(conceptSequence);
                buf.append(value.toString());
                if (i < maxIndex) {
                    buf.append(",");
                }
            }
            buf.append(']');
            return buf.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void addStampRecord(int destinationSequence, int typeSequence, int stamp, int recordFlags) {
        TypeStampTaxonomyRecords conceptSequenceStampRecordsUnpacked;
        if (conceptSequenceRecordMap.containsKey(destinationSequence)) {
            conceptSequenceStampRecordsUnpacked = conceptSequenceRecordMap.get(destinationSequence);
        } else {
            conceptSequenceStampRecordsUnpacked = new TypeStampTaxonomyRecords();
            conceptSequenceRecordMap.put(destinationSequence, conceptSequenceStampRecordsUnpacked);
        }
        conceptSequenceStampRecordsUnpacked.addStampRecord(typeSequence, stamp, recordFlags);
    }

    private class PackConceptSequenceStampRecords implements IntObjectProcedure<TypeStampTaxonomyRecords> {

        int[] taxonomyRecordArray = new int[length()];
        int destinationPosition = 0;

        @Override
        public boolean apply(int conceptSequence, TypeStampTaxonomyRecords stampRecordsUnpacked) {
            stampRecordsUnpacked.addToIntArray(conceptSequence, taxonomyRecordArray, destinationPosition);
            destinationPosition += stampRecordsUnpacked.length();
            return true;
        }
    }
}
