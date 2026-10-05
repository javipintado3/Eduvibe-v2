import { Directive, Input, TemplateRef, inject } from '@angular/core';

/**
 * Plantilla de las celdas de una columna, equivalente al `renderCell` de una
 * columna de MUI DataGrid:
 *
 *   <ng-template appCeldaTabla="rol" let-usuario>...</ng-template>
 *
 * La fila llega como `let-...`. Una columna sin plantilla pinta el valor
 * de `fila[campo]` tal cual.
 */
@Directive({
  selector: 'ng-template[appCeldaTabla]',
  standalone: true,
})
export class CeldaTablaDirective {
  readonly plantilla = inject<TemplateRef<unknown>>(TemplateRef);

  @Input({ required: true, alias: 'appCeldaTabla' }) campo = '';
}
