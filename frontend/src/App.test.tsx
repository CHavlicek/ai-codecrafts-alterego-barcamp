import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'

// downscalePhoto is called indirectly via PhotoIntake on mount of AlterEgoPage.
// Mock to avoid needing Canvas in jsdom.
vi.mock('./features/alterego/lib/downscalePhoto', () => ({
  downscalePhoto: vi.fn(async (blob: Blob) => blob),
}))
vi.mock('./features/alterego/services/alterEgoClient', () => ({
  generateAlterEgo: vi.fn(),
}))

describe('App', () => {
  test('mounts the AlterEgoPage inside providers', () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    })
    render(
      <QueryClientProvider client={client}>
        <App />
      </QueryClientProvider>,
    )
    expect(
      screen.getByRole('heading', { name: /AI @ Verbund 2026/i, level: 1 }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /take a photo of yourself/i })).toBeInTheDocument()
  })
})
