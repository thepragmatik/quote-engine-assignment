import QuoteForm from './components/QuoteForm'

/**
 * Root application component hosting the Commission Quote generation workflow.
 */
function App() {
  return (
    <main className="min-h-screen bg-slate-50 py-10 px-4 sm:px-6 lg:px-8">
      <QuoteForm />
    </main>
  )
}

export default App
