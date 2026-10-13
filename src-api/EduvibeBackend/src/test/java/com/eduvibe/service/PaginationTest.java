package com.eduvibe.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.eduvibe.dto.common.PageResponse;

class PaginationTest {

    @Test
    @DisplayName("Conserva la página y el tamaño pedidos y descarta el orden")
    void keepsPageAndSizeButDropsSort() {
        Pageable requested = PageRequest.of(3, 7, Sort.by(Sort.Direction.DESC, "loQueSea"));

        Pageable cleans = Pagination.unsorted(requested);

        assertThat(cleans.getPageNumber()).isEqualTo(3);
        assertThat(cleans.getPageSize()).isEqualTo(7);
        assertThat(cleans.getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("Sin texto de búsqueda el patrón casa con todo")
    void blankSearchMatchesEverything() {
        assertThat(Pagination.searchPattern(null)).isEqualTo("%");
        assertThat(Pagination.searchPattern("")).isEqualTo("%");
        assertThat(Pagination.searchPattern("   ")).isEqualTo("%");
    }

    @Test
    @DisplayName("El texto de búsqueda se recorta y pasa a minúsculas dentro de un LIKE")
    void searchTextIsTrimmedAndLowercased() {
        assertThat(Pagination.searchPattern("  Matemáticas ")).isEqualTo("%matemáticas%");
    }

    @Test
    @DisplayName("El envoltorio con contenido ya construido conserva los datos de paginación")
    void wrapsPrebuiltContentKeepingPagingData() {
        var pagina = new PageImpl<>(List.of("a", "b"), PageRequest.of(2, 2), 6);

        PageResponse<Integer> answer = PageResponse.of(pagina, List.of(1, 2));

        assertThat(answer.contenido()).containsExactly(1, 2);
        assertThat(answer.pagina()).isEqualTo(2);
        assertThat(answer.tamano()).isEqualTo(2);
        assertThat(answer.totalElementos()).isEqualTo(6);
        assertThat(answer.totalPaginas()).isEqualTo(3);
        assertThat(answer.ultima()).isTrue();
    }
}
