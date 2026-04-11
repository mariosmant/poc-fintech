/** Loading spinner component. */
export function Spinner({ className = '' }: { className?: string }) {
  return (
    <div className={`flex items-center justify-center ${className}`}>
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600" />
    </div>
  );
}

/** Error message display. */
export function ErrorMessage({ message }: { message: string }) {
  return (
    <div className="bg-danger-50 border border-red-200 text-danger-700 px-4 py-3 rounded-lg text-sm">
      <strong>Error:</strong> {message}
    </div>
  );
}

/** Stat card used on the dashboard. */
export function StatCard({
  label,
  value,
  icon,
  colour = 'primary',
}: {
  label: string;
  value: string | number;
  icon: string;
  colour?: 'primary' | 'success' | 'danger' | 'warning';
}) {
  const colourMap = {
    primary: 'bg-primary-50 text-primary-700',
    success: 'bg-success-50 text-success-700',
    danger: 'bg-danger-50 text-danger-700',
    warning: 'bg-warning-50 text-warning-700',
  };
  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-gray-500 font-medium">{label}</p>
          <p className="text-2xl font-bold mt-1">{value}</p>
        </div>
        <div className={`text-3xl p-3 rounded-lg ${colourMap[colour]}`}>{icon}</div>
      </div>
    </div>
  );
}

