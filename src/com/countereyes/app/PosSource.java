package com.countereyes.app;

import java.util.List;

/** Kasa/POS kaynağı: kapanan adisyonları döndürür. Uygulama yalnız okur. */
interface PosSource {
    List<Closure> fetch(long since, long until) throws Exception;

    /** Durum satırında gösterilecek kısa ad. */
    String label();
}
