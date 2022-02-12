package com.example.distinscale.domain.models;

public class PreprocessParameters {
    public final int GB_size;
    public final int GB_sX;
    public final int GB_sY;
    public final int C_t1;
    public final int C_t2;
    public final int k_size;
    public final int d_i;
    public final int e_i;

    public PreprocessParameters(int GB_size, int GB_sX, int GB_sY, int C_t1, int C_t2, int k_size, int d_i, int e_i){
        this.GB_size = GB_size;
        this.GB_sX = GB_sX;
        this.GB_sY = GB_sY;
        this.C_t1 = C_t1;
        this.C_t2 = C_t2;
        this.k_size = k_size;
        this.d_i = d_i;
        this.e_i = e_i;
    }
}
