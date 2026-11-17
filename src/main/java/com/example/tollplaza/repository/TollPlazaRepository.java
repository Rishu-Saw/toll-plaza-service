package com.example.tollplaza.repository;

import com.example.tollplaza.model.TollPlaza;

import java.util.List;

public interface TollPlazaRepository {

    List<TollPlaza> findAll();

    void saveAll(List<TollPlaza> tollPlazas);

    void add(TollPlaza tollPlaza);

    int count();

    void clear();
}
