import { Component, inject, signal } from '@angular/core';
import { NgFor } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ClasesService } from '../../../core/services/clases.service';
import { ModoVistaClase } from '../../../core/models';
import { AvisoComponent } from '../../../shared/aviso/aviso.component';
import { CampoFormularioComponent } from '../../../shared/formulario/campo-formulario.component';
import { PaginaFormularioComponent } from '../../../shared/formulario/pagina-formulario.component';
import { SeccionFormularioComponent } from '../../../shared/formulario/seccion-formulario.component';
import { PALETA_CLASE } from '../../../shared/paleta-clase';
import { SubidaArchivoComponent } from '../../../shared/subida-archivo/subida-archivo.component';

/**
 * Alta de una clase. Es una página y no una ventana: el formulario tiene
 * varias partes (datos, apariencia, organización) y necesita sitio, y así se
 * puede enlazar y el botón "atrás" del navegador hace lo esperado.
 */
@Component({
  selector: 'app-nueva-clase',
  standalone: true,
  imports: [
    NgFor, ReactiveFormsModule,
    PaginaFormularioComponent, SeccionFormularioComponent, CampoFormularioComponent,
    SubidaArchivoComponent, AvisoComponent,
  ],
  templateUrl: './nueva-clase.component.html',
  styleUrl: './nueva-clase.component.css',
})
export class NuevaClaseComponent {

  private readonly clasesService = inject(ClasesService);
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly paleta = PALETA_CLASE;

  readonly guardando = signal(false);
  readonly error = signal<string | null>(null);

  readonly formulario = this.fb.nonNullable.group({
    name: ['', [Validators.required]],
    subject: [''],
    color: [PALETA_CLASE[0].valor],
    imageUrl: [''],
    viewMode: this.fb.nonNullable.control<ModoVistaClase>('structured'),
  });

  /** Error de un campo, solo cuando ya se ha tocado o se ha intentado guardar. */
  errorDe(campo: 'name'): string | null {
    const control = this.formulario.controls[campo];
    return control.touched && control.hasError('required') ? 'Este campo es obligatorio' : null;
  }

  /** Quita los espacios sobrantes al salir del campo. */
  recortar(campo: 'name' | 'subject' | 'imageUrl'): void {
    const control = this.formulario.controls[campo];
    control.setValue(control.value.trim());
  }

  guardar(): void {
    this.formulario.markAllAsTouched();

    if (this.formulario.invalid || this.guardando()) {
      return;
    }

    this.guardando.set(true);
    this.error.set(null);

    const { name, subject, color, imageUrl, viewMode } = this.formulario.getRawValue();

    this.clasesService.crear({
      name,
      subject: subject || undefined,
      color,
      imageUrl: imageUrl.trim() || undefined,
      viewMode,
    }).subscribe({
      next: () => this.router.navigate(['/clases'], { state: { aviso: `Clase «${name}» creada` } }),
      error: (err) => {
        this.guardando.set(false);
        this.error.set(AvisoComponent.mensajeDe(err));
      },
    });
  }
}
