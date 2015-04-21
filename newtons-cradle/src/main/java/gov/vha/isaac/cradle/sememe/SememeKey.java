package gov.vha.isaac.cradle.sememe;

/**
 * Created by kec on 12/18/14.
 */
public class SememeKey implements Comparable<SememeKey> {
    int key1;
    int sememeSequence;

    public SememeKey(int key1, int sememeSequence) {
        this.key1 = key1;
        this.sememeSequence = sememeSequence;
    }

    @Override
    public int compareTo(SememeKey o) {
        if (key1 != o.key1) {
            if (key1 < o.key1) {
                return -1;
            }
            return 1;
        }
        if (sememeSequence == o.sememeSequence) {
            return 0;
        }
        if (sememeSequence < o.sememeSequence) {
            return -1;
        }
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SememeKey sememeKey = (SememeKey) o;

        if (key1 != sememeKey.key1) return false;
        return sememeSequence == sememeKey.sememeSequence;
    }

    @Override
    public int hashCode() {
        int result = key1;
        result = 31 * result + sememeSequence;
        return result;
    }

    public int getKey1() {
        return key1;
    }

    public int getSememeSequence() {
        return sememeSequence;
    }

    @Override
    public String toString() {
        return "SememeKey{" +
                "key1=" + key1 +
                ", sememeSequence=" + sememeSequence +
                '}';
    }
}
