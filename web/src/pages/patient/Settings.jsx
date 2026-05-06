import { useCallback, useEffect, useState } from 'react'
import { useBeforeUnload } from 'react-router-dom'
import DashboardLayout from '../../components/DashboardLayout'
import { getMyProfile, updateMyProfile } from '../../api/profileApi'
import { useAuth } from '../../hooks/useAuth'
import {
  User, Shield, Save, Camera, Eye, EyeOff,
  AlertCircle, CheckCircle2, Check, X, RotateCcw
} from 'lucide-react'

function resolveErrorMessage(err, fallback) {
  const apiError = err?.response?.data
  if (apiError?.errors) return Object.values(apiError.errors)[0]
  return apiError?.detail || apiError?.message || fallback
}

export default function PatientSettings() {
  const { login, user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [showPasswords, setShowPasswords] = useState(false)

  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phoneNumber: '' })
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })

  const [profileError, setProfileError] = useState('')
  const [securityError, setSecurityError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [saving, setSaving] = useState(false)

  useEffect(() => { loadInitialData() }, [])

  const loadInitialData = () => {
    getMyProfile()
        .then((data) => {
          setProfile(data)
          resetFormStates(data)
        })
        .catch(() => setProfileError('Failed to load profile.'))
  }

  const resetFormStates = (data) => {
    const digits = (data.phoneNumber || '').replace(/^\+63/, '')
    setForm({
      firstName: data.firstName || '',
      lastName: data.lastName || '',
      email: data.email || '',
      phoneNumber: digits,
    })
    setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
  }

  const handleDiscard = () => {
    if (profile) resetFormStates(profile)
    setSecurityError(''); setProfileError(''); setSuccessMessage('')
  }

  // --- Live Validations & Dirty Checking ---
  const isEmailValid = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)
  const isPhoneValid = form.phoneNumber.length === 10

  const passwordRules = {
    length: passwordForm.newPassword.length >= 8,
    uppercase: /[A-Z]/.test(passwordForm.newPassword),
    number: /[0-9]/.test(passwordForm.newPassword),
    special: /[!@#$%^&*(),.?":{}|<>]/.test(passwordForm.newPassword),
  }
  const isPasswordValid = Object.values(passwordRules).every(Boolean)
  const doPasswordsMatch = passwordForm.newPassword === passwordForm.confirmPassword && passwordForm.newPassword !== ''

  // Dirty Check: Compare current inputs vs the saved profile
  const isProfileDirty = profile && (
      form.firstName !== (profile.firstName || '') ||
      form.lastName !== (profile.lastName || '') ||
      form.email !== (profile.email || '') ||
      form.phoneNumber !== (profile.phoneNumber || '').replace(/^\+63/, '')
  )

  const isPasswordDirty = Boolean(passwordForm.newPassword || passwordForm.confirmPassword || passwordForm.currentPassword)
  const hasUnsavedChanges = Boolean(isProfileDirty || isPasswordDirty)

  const handleBeforeUnload = useCallback((event) => {
    if (hasUnsavedChanges) {
      event.preventDefault()
      event.returnValue = ''
    }
  }, [hasUnsavedChanges])

  useBeforeUnload(handleBeforeUnload)

  useEffect(() => {
    if (!hasUnsavedChanges) return undefined

    const confirmLeave = () => window.confirm("if you leave, your changes won't be saved")

    const onDocumentClick = (event) => {
      const anchor = event.target.closest('a[href]')
      if (!anchor) return

      if (anchor.target === '_blank' || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return
      }

      const href = anchor.getAttribute('href')
      if (!href || href.startsWith('#')) return

      const destination = new URL(href, window.location.origin)
      const current = new URL(window.location.href)
      const isSameRoute =
        destination.pathname === current.pathname
        && destination.search === current.search
        && destination.hash === current.hash

      if (isSameRoute) return

      if (!confirmLeave()) {
        event.preventDefault()
        event.stopPropagation()
      }
    }

    const onPopState = () => {
      if (confirmLeave()) return
      window.history.pushState(null, '', window.location.href)
    }

    window.history.pushState(null, '', window.location.href)
    document.addEventListener('click', onDocumentClick, true)
    window.addEventListener('popstate', onPopState)

    return () => {
      document.removeEventListener('click', onDocumentClick, true)
      window.removeEventListener('popstate', onPopState)
    }
  }, [hasUnsavedChanges])

  // Save Logic: Must be dirty, valid, and if password is touched, must be fully compliant
  const canSave = !saving && (
      (isProfileDirty && isEmailValid && isPhoneValid && !isPasswordDirty) ||
      (isPasswordDirty && isPasswordValid && doPasswordsMatch && passwordForm.currentPassword && isEmailValid && isPhoneValid) ||
      (isProfileDirty && isPasswordDirty && isPasswordValid && doPasswordsMatch && passwordForm.currentPassword && isEmailValid && isPhoneValid)
  )

  function updateField(event) {
    const { name, value } = event.target
    if (name === 'phoneNumber') {
      setForm(prev => ({ ...prev, phoneNumber: value.replace(/\D/g, '').slice(0, 10) }))
      return
    }
    setForm(prev => ({ ...prev, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!canSave) return
    setSaving(true); setSuccessMessage(''); setProfileError(''); setSecurityError('')

    try {
      const payload = {
        firstName: form.firstName,
        lastName: form.lastName,
        email: form.email,
        phoneNumber: `+63${form.phoneNumber}`
      }
      if (isPasswordDirty) {
        payload.currentPassword = passwordForm.currentPassword
        payload.newPassword = passwordForm.newPassword
      }
      const response = await updateMyProfile(payload)
      login(response.token, response.user)
      setProfile(response.user)
      setSuccessMessage('Settings updated successfully.')
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })
    } catch (err) {
      const msg = resolveErrorMessage(err, 'Update failed.')
      msg.toLowerCase().includes('password') ? setSecurityError(msg) : setProfileError(msg)
    } finally { setSaving(false) }
  }

  const ValidationItem = ({ label, met }) => (
      <div className={`flex items-center gap-1.5 text-[11px] ${met ? 'text-teal' : 'text-muted-foreground'}`}>
        {met ? <Check className="h-3 w-3" /> : <X className="h-3 w-3" />}
        <span>{label}</span>
      </div>
  )

  return (
      <DashboardLayout>
        <div className="space-y-6">
          {/* Header */}
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-2xl font-bold">Settings</h1>
              <p className="text-muted-foreground font-body">Manage your account and security</p>
            </div>
            <div className="flex items-center gap-3">
              <button
                  type="button"
                  onClick={handleDiscard}
                  disabled={!isProfileDirty && !isPasswordDirty}
                  className="inline-flex h-10 items-center justify-center rounded-md border border-input bg-background px-4 text-sm font-medium hover:bg-muted disabled:opacity-50 transition-all"
              >
                <RotateCcw className="mr-2 h-4 w-4" /> Discard
              </button>
              <button
                  type="submit"
                  form="patient-settings-form"
                  disabled={!canSave}
                  className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90 disabled:opacity-50 transition-all"
              >
                <Save className="mr-2 h-4 w-4" /> {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </div>

          {successMessage && (
              <div className="flex items-center gap-2 rounded-lg border border-teal/20 bg-teal/10 p-4 text-teal animate-in fade-in slide-in-from-top-2">
                <CheckCircle2 className="h-5 w-5" />
                <p className="text-sm font-medium">{successMessage}</p>
              </div>
          )}

          <form id="patient-settings-form" onSubmit={handleSubmit} className="space-y-6">

            {/* Section: Profile Information */}
            <div className="rounded-2xl border border-border bg-card shadow-card overflow-hidden">
              <div className="flex items-center gap-2 border-b border-border p-5">
                <div className="rounded-lg bg-primary-soft p-2">
                  <User className="h-4 w-4 text-primary" />
                </div>
                <h2 className="font-semibold text-lg">Profile Information</h2>
              </div>

              <div className="p-5 space-y-4">
                {profileError && (
                    <div className="flex items-center gap-2 rounded-md bg-destructive/10 p-3 text-destructive border border-destructive/20">
                      <AlertCircle className="h-4 w-4" />
                      <p className="text-xs font-medium">{profileError}</p>
                    </div>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">First Name</label>
                    <input name="firstName" value={form.firstName} onChange={updateField} required className="h-10 w-full rounded-md border border-input bg-muted/50 px-3 text-sm focus:ring-2 focus:ring-primary/20 outline-none" />
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Last Name</label>
                    <input name="lastName" value={form.lastName} onChange={updateField} required className="h-10 w-full rounded-md border border-input bg-muted/50 px-3 text-sm focus:ring-2 focus:ring-primary/20 outline-none" />
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Email Address</label>
                  <div className="relative">
                    <input
                        name="email" value={form.email} onChange={updateField} required
                        className={`h-10 w-full rounded-md border px-3 pr-10 text-sm transition-all outline-none focus:ring-2 focus:ring-primary/20 ${form.email && !isEmailValid ? 'border-destructive/50 bg-destructive/5' : isEmailValid ? 'border-teal/50 bg-teal/5' : 'border-input bg-muted/50'}`}
                    />
                    {isEmailValid && <Check className="absolute right-3 top-2.5 h-4 w-4 text-teal" />}
                  </div>
                </div>

                <div className="space-y-2">
                  <label className="text-sm font-medium">Phone Number</label>
                  <div className={`flex h-10 items-center rounded-md border transition-all ${isPhoneValid ? 'border-teal/50 bg-teal/5' : 'border-input bg-muted/50'}`}>
                    <span className="px-3 text-sm text-muted-foreground border-r border-input">+63</span>
                    <input name="phoneNumber" value={form.phoneNumber} onChange={updateField} maxLength={10} className="w-full bg-transparent px-3 text-sm outline-none" />
                  </div>
                  <div className="flex justify-between items-center px-1">
                    <p className="text-[10px] text-muted-foreground">Enter 10 digits after +63</p>
                    <span className={`text-[10px] ${isPhoneValid ? 'text-teal' : 'text-muted-foreground'}`}>{form.phoneNumber.length}/10</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Section: Security */}
            <div className="rounded-2xl border border-border bg-card shadow-card overflow-hidden">
              <div className="flex items-center justify-between border-b border-border p-5">
                <div className="flex items-center gap-2">
                  <div className="rounded-lg bg-teal-soft p-2">
                    <Shield className="h-4 w-4 text-teal" />
                  </div>
                  <h2 className="font-semibold text-lg">Security</h2>
                </div>
                <button type="button" onClick={() => setShowPasswords(!showPasswords)} className="text-xs flex items-center gap-1 text-muted-foreground hover:text-primary transition-colors">
                  {showPasswords ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                  {showPasswords ? 'Hide' : 'Show'} Passwords
                </button>
              </div>

              <div className="p-5 space-y-4">
                {securityError && (
                    <div className="flex items-center gap-2 rounded-md bg-destructive/10 p-3 text-destructive border border-destructive/20">
                      <AlertCircle className="h-4 w-4" />
                      <p className="text-xs font-medium">{securityError}</p>
                    </div>
                )}

                <div className="grid gap-4 sm:grid-cols-3">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Current Password</label>
                    <input
                        type={showPasswords ? "text" : "password"} value={passwordForm.currentPassword}
                        onChange={(e) => setPasswordForm(p => ({...p, currentPassword: e.target.value}))}
                        placeholder="••••••••" className="h-10 w-full rounded-md border border-input bg-muted/50 px-3 text-sm focus:ring-2 focus:ring-primary/20 outline-none"
                    />
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium">New Password</label>
                    <input
                        type={showPasswords ? "text" : "password"} value={passwordForm.newPassword}
                        onChange={(e) => setPasswordForm(p => ({...p, newPassword: e.target.value}))}
                        placeholder="••••••••"
                        className={`h-10 w-full rounded-md border px-3 text-sm focus:ring-2 outline-none transition-all ${passwordForm.newPassword && !isPasswordValid ? 'border-destructive/50 bg-destructive/5' : isPasswordValid ? 'border-teal/50 bg-teal/5' : 'border-input bg-muted/50'}`}
                    />
                    {passwordForm.newPassword && (
                        <div className="grid grid-cols-2 gap-y-1 mt-2">
                          <ValidationItem label="8+ Characters" met={passwordRules.length} />
                          <ValidationItem label="Uppercase" met={passwordRules.uppercase} />
                          <ValidationItem label="Number" met={passwordRules.number} />
                          <ValidationItem label="Special Symbol" met={passwordRules.special} />
                        </div>
                    )}
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Confirm Password</label>
                    <input
                        type={showPasswords ? "text" : "password"} value={passwordForm.confirmPassword}
                        onChange={(e) => setPasswordForm(p => ({...p, confirmPassword: e.target.value}))}
                        placeholder="••••••••"
                        className={`h-10 w-full rounded-md border px-3 text-sm focus:ring-2 outline-none transition-all ${passwordForm.confirmPassword && !doPasswordsMatch ? 'border-destructive/50 bg-destructive/5' : doPasswordsMatch ? 'border-teal/50 bg-teal/5' : 'border-input bg-muted/50'}`}
                    />
                    {passwordForm.confirmPassword && !doPasswordsMatch && <p className="text-[10px] text-destructive">Passwords do not match.</p>}
                  </div>
                </div>
              </div>
            </div>
          </form>
        </div>
      </DashboardLayout>
  )
}