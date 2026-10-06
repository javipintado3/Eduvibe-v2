import { Component, OnInit, inject, signal } from '@angular/core';
import { NgIf } from '@angular/common';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';

import { OrganizacionService } from '../../../../core/services/organizacion.service';
import { Organizacion } from '../../../../core/models';
import { AvisoComponent } from '../../../../shared/aviso/aviso.component';

/**
 * Dominio de correo permitido para las altas.
 *
 * Con un dominio puesto, solo se pueden dar de alta emails de ese dominio, que
 * es lo habitual en un centro con correo corporativo. Vacío, no hay límite.
 * Las cuentas que ya existen con otro dominio no se tocan.
 */
@Component({
  selector: 'app-dominio-permitido',
  standalone: true,
  imports: [NgIf, ReactiveFormsModule, AvisoComponent],
  templateUrl: './dominio-permitido.component.html',
})
export class DominioPermitidoComponent implements OnInit {

  private readonly fb = inject(FormBuilder);
  private readonly organizacionService = inject(OrganizacionService);

  readonly formulario = this.fb.nonNullable.group({ dominio: [''] });

  readonly organizacion = signal<Organizacion | null>(null);
  readonly guardando = signal(false);
  readonly guardado = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.organizacionService.obtener().subscribe({
      next: (organizacion) => this.mostrar(organizacion),
      error: (err) => this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido cargar el dominio')),
    });
  }

  guardar(): void {
    if (this.guardando()) {
      return;
    }

    this.guardando.set(true);
    this.guardado.set(false);
    this.error.set(null);

    this.organizacionService.cambiarDominioPermitido(this.formulario.getRawValue().dominio).subscribe({
      next: (organizacion) => {
        this.mostrar(organizacion);
        this.guardando.set(false);
        this.guardado.set(true);
      },
      error: (err) => {
        this.guardando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err, 'No se ha podido guardar el dominio'));
      },
    });
  }

  private mostrar(organizacion: Organizacion): void {
    this.organizacion.set(organizacion);
    this.formulario.setValue({ dominio: organizacion.allowedDomain ?? '' });
  }
}
