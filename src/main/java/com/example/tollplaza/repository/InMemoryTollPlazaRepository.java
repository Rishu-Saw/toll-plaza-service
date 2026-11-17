package com.example.tollplaza.repository;

import com.example.tollplaza.model.TollPlaza;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class InMemoryTollPlazaRepository implements TollPlazaRepository {

    private final List<TollPlaza> tollPlazas = new CopyOnWriteArrayList<>();

    @Override
    public List<TollPlaza> findAll() {
        return Collections.unmodifiableList(new ArrayList<>(tollPlazas));
    }

    @Override
    public void saveAll(List<TollPlaza> list) {
        if (list != null) {
            tollPlazas.addAll(list);
        }
    }

    @Override
    public void add(TollPlaza tollPlaza) {
        if (tollPlaza != null) {
            tollPlazas.add(tollPlaza);
        }
    }

    @Override
    public int count() {
        return tollPlazas.size();
    }

    @Override
    public void clear() {
        tollPlazas.clear();
    }
}
