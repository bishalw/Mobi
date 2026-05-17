import client from './client'
import type { ProductRequest, ProductResponse } from '@/types'

export const getProducts = () =>
  client.get<ProductResponse[]>('/productapi/products').then((r) => r.data)

export const getProduct = (id: number) =>
  client.get<ProductResponse>(`/productapi/products/${id}`).then((r) => r.data)

export const createProduct = (data: ProductRequest) =>
  client.post<ProductResponse>('/productapi/products', data).then((r) => r.data)

export const updateProduct = (id: number, data: ProductRequest) =>
  client.put<ProductResponse>(`/productapi/products/${id}`, data).then((r) => r.data)

export const deleteProduct = (id: number) =>
  client.delete(`/productapi/products/${id}`)
