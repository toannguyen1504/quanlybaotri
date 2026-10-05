import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class Api {
  readonly baseUrl = '/api/v1';
  constructor(private http: HttpClient) {}
  get<T>(path: string, params?: Record<string, string | number | boolean | undefined>) {
    let p = new HttpParams();
    Object.entries(params ?? {}).forEach(([k, v]) => {
      if (v !== undefined && v !== '') p = p.set(k, String(v));
    });
    return this.http.get<T>(this.baseUrl + path, { params: p });
  }
  post<T>(path: string, body: unknown = {}) {
    return this.http.post<T>(this.baseUrl + path, body);
  }
  put<T>(path: string, body: unknown) {
    return this.http.put<T>(this.baseUrl + path, body);
  }
  delete<T>(path: string) {
    return this.http.delete<T>(this.baseUrl + path);
  }
  upload<T>(path: string, file: File, fields?: Record<string, string | undefined>) {
    const form = new FormData();
    form.append('file', file);
    Object.entries(fields ?? {}).forEach(([key, value]) => {
      if (value) form.append(key, value);
    });
    return this.http.post<T>(this.baseUrl + path, form);
  }
  uploadWithProgress<T>(path: string, file: File, fields?: Record<string, string | undefined>) {
    const form = new FormData();
    form.append('file', file);
    Object.entries(fields ?? {}).forEach(([key, value]) => {
      if (value) form.append(key, value);
    });
    return this.http.post<T>(this.baseUrl + path, form, {
      observe: 'events',
      reportProgress: true,
    });
  }
  download(path: string, params?: Record<string, string | number | boolean | undefined>) {
    let p = new HttpParams();
    Object.entries(params ?? {}).forEach(([k, v]) => {
      if (v !== undefined && v !== '') p = p.set(k, String(v));
    });
    return this.http.get(this.baseUrl + path, { params: p, responseType: 'blob' });
  }
}
