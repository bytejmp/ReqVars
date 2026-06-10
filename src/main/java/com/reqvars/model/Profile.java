package com.reqvars.model;

import java.util.ArrayList;
import java.util.List;

public class Profile {

    private String name;
    private List<Variable> variables;

    public Profile(String name) {
        this.name = name;
        this.variables = new ArrayList<>();
    }

    public Profile(String name, List<Variable> variables) {
        this.name = name;
        this.variables = new ArrayList<>(variables);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(List<Variable> variables) {
        this.variables = new ArrayList<>(variables);
    }

    @Override
    public String toString() {
        return name;
    }
}
