import axios from 'axios'
import { authToken } from '@/lib/authToken'

const client = axios.create()

client.interceptors.request.use((config) => {
  const token = authToken.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      authToken.clear()
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default client
