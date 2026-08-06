export {}

declare global {
    interface Window {
        phoneAuthCallback?: (data: {
            name: string;
            phoneNumber: string;
            ci: string
        }) => void
    }
}