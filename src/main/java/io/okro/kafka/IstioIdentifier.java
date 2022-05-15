package io.okro.kafka;

public class IstioIdentifier {
    private String ns;
    private String sa;

    public void setNs(String ns) {
        this.ns = ns;
    }

    public void setSa(String sa) {
        this.sa = sa;
    }

    public String getNs() {
        return ns;
    }

    public String getSa() {
        return sa;
    }

    public boolean isOkay() {
        return sa != null && ns != null;
    }

    public String toString() {
        return String.format("%s-%s", ns, sa);
    }

}
