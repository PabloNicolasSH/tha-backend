package org.scoutsdecanarias.tha_backend.features.scout_group;

import java.util.Arrays;
import java.util.Optional;

/** Lista fija de islas de Canarias. En la base de datos se guarda el nombre del valor ({@code GRAN_CANARIA}). */
public enum Island {
    EL_HIERRO("El Hierro"),
    FUERTEVENTURA("Fuerteventura"),
    GRAN_CANARIA("Gran Canaria"),
    LA_GOMERA("La Gomera"),
    LA_GRACIOSA("La Graciosa"),
    LA_PALMA("La Palma"),
    LANZAROTE("Lanzarote"),
    TENERIFE("Tenerife");

    private final String label;

    Island(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Optional<Island> parse(String code) {
        return Arrays.stream(values()).filter(i -> i.name().equals(code)).findFirst();
    }
}
