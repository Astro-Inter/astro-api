package com.astro.api.unit.model;

import com.astro.api.unit.mapper.UnitCoordinatesConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "unidade_endereco")
public class UnitAddress {
    @Id
    @Column(name = "unidade_id")
    public Long id;

    @Column(name = "cep", nullable = false)
    public String cep;

    @Column(name = "rua", nullable = false)
    public String street;

    @Column(name = "numero", length = 30)
    public String number;

    @Column(name = "cidade", nullable = false)
    public String city;

    @Column(name = "bairro", nullable = false)
    public String neighborhood;

    @Column(name = "estado", nullable = false)
    public String state;

    @Column(name = "complemento")
    public String addressLine2;

    @Column(name = "geolocalizacao", columnDefinition = "point")
    @Convert(converter = UnitCoordinatesConverter.class)
    @ColumnTransformer(read = "cast(geolocalizacao as text)", write = "cast(? as point)")
    public UnitCoordinates geolocation;

    @OneToOne
    @MapsId
    @JoinColumn(name = "unidade_id", nullable = false)
    public Unit unit;
}
