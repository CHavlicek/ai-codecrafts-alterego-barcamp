import { AlterEgoPage } from './features/alterego/AlterEgoPage'
import { LiveRegionProvider } from './components/LiveRegion'

/**
 * Application root. Mounts the singleton ARIA live region (FR-022)
 * and the 005 atmosphere layer (aurora + grain + vignette — see
 * `.atmosphere` in index.css) once at the top of the tree.
 */
function App() {
  return (
    <LiveRegionProvider>
      <div className="atmosphere" aria-hidden="true">
        <div className="atmosphere__aurora" />
        <div className="atmosphere__grain" />
        <div className="atmosphere__vignette" />
      </div>
      <AlterEgoPage />
    </LiveRegionProvider>
  )
}

export default App
