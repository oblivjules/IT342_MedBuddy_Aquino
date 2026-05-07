import { useCallback, useEffect, useState } from 'react'
import { useBeforeUnload } from 'react-router-dom'
import Cropper from 'react-easy-crop'
import DashboardLayout from '../../components/DashboardLayout'
import UserAvatar from './UserAvatar'
import { getMyProfile, updateMyProfile, uploadDoctorProfileImage } from './profileApi'
import { clearDoctorsCache } from './userApi'
import { getSpecializations } from '../findDoctor/specializationApi'
import { useAuth } from '../auth/useAuth'
import {
  User, Shield, Save, Camera, Eye, EyeOff,
  AlertCircle, CheckCircle2, Check, X, RotateCcw, XCircle
} from 'lucide-react'

function resolveErrorMessage(err, fallback) {
  const apiError = err?.response?.data
  if (apiError?.errors) return Object.values(apiError.errors)[0]
  return apiError?.detail || apiError?.message || fallback
}

function createImage(url) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.addEventListener('load', () => resolve(image))
    image.addEventListener('error', reject)
    image.src = url
  })
}

async function getCroppedImage(imageSrc, cropPixels) {
  const image = await createImage(imageSrc)
  const canvas = document.createElement('canvas')
  const context = canvas.getContext('2d')

  if (!context) {
    throw new Error('Unable to crop image.')
  }

  canvas.width = Math.round(cropPixels.width)
  canvas.height = Math.round(cropPixels.height)

  context.drawImage(
      image,
      cropPixels.x,
      cropPixels.y,
      cropPixels.width,
      cropPixels.height,
      0,
      0,
      cropPixels.width,
      cropPixels.height,
  )

  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        reject(new Error('Unable to crop image.'))
        return
      }
      resolve(blob)
    }, 'image/png', 1)
  })
}

export default function DoctorSettings() {
  const { login, updateUser, user } = useAuth()
  const [profile, setProfile] = useState(null)
  const [allSpecializations, setAllSpecializations] = useState([])
  const [showPasswords, setShowPasswords] = useState(false)
  const [uploadingImage, setUploadingImage] = useState(false)

  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', phoneNumber: '' })
  const [selectedSpecializationIds, setSelectedSpecializationIds] = useState([])
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })

  const [profileError, setProfileError] = useState('')
  const [securityError, setSecurityError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [saving, setSaving] = useState(false)
  const [imageDirty, setImageDirty] = useState(false)

  const [cropOpen, setCropOpen] = useState(false)
  const [cropSource, setCropSource] = useState('')
  const [crop, setCrop] = useState({ x: 0, y: 0 })
  const [zoom, setZoom] = useState(1)
  const [croppedAreaPixels, setCroppedAreaPixels] = useState(null)

  useEffect(() => { loadInitialData() }, [])

  const loadInitialData = () => {
    Promise.all([getMyProfile(), getSpecializations()])
        .then(([data, specializationData]) => {
          console.log('[PROFILE_IMAGE][INIT] profile loaded:', data)
          console.log('[PROFILE_IMAGE][INIT] profileImageUrl:', data?.profileImageUrl)
          setProfile(data)
          setAllSpecializations(Array.isArray(specializationData) ? specializationData : [])
          resetFormStates(data, specializationData)
        })
        .catch(() => setProfileError('Failed to load profile.'))
  }

  const resetFormStates = (data, specializationData) => {
    const digits = (data.phoneNumber || '').replace(/^\+63/, '')
    setForm({
      firstName: data.firstName || '',
      lastName: data.lastName || '',
      email: data.email || '',
      phoneNumber: digits,
    })
    setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' })

    if (Array.isArray(data.specializations) && Array.isArray(specializationData)) {
      const selected = specializationData
          .filter((spec) => data.specializations.includes(spec.name))
          .map((spec) => spec.id)
      setSelectedSpecializationIds(selected)
    }

    setImageDirty(false)
  }

  const handleDiscard = () => {
    if (profile) resetFormStates(profile, allSpecializations)
    setSecurityError(''); setProfileError(''); setSuccessMessage('')
    setImageDirty(false)
  }

  useEffect(() => {
    return () => {
      if (cropSource) {
        URL.revokeObjectURL(cropSource)
      }
    }
  }, [cropSource])

  const toggleSpecialization = (id) => {
    setSelectedSpecializationIds((current) => (
      current.includes(id)
        ? current.filter((value) => value !== id)
        : [...current, id]
    ))
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

  // 1. Check if Profile Data has changed
  const isProfileDirty = profile && (
      form.firstName !== (profile.firstName || '') ||
      form.lastName !== (profile.lastName || '') ||
      form.email !== (profile.email || '') ||
      form.phoneNumber !== (profile.phoneNumber || '').replace(/^\+63/, '') ||
      JSON.stringify([...selectedSpecializationIds].sort()) !== JSON.stringify(
          allSpecializations.filter(s => profile.specializations?.includes(s.name)).map(s => s.id).sort()
      ) ||
      imageDirty
  )

  // 2. Check if Password fields are being touched
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

  // 3. Final Save Logic
  const canSave = !saving && (
      // Case A: Only profile changed
      (isProfileDirty && isEmailValid && isPhoneValid && !isPasswordDirty) ||
      // Case B: Password is being changed (must be valid + profile must be valid)
      (isPasswordDirty && isPasswordValid && doPasswordsMatch && passwordForm.currentPassword && isEmailValid && isPhoneValid) ||
      // Case C: Both changed (handled by the logic above)
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
        phoneNumber: `+63${form.phoneNumber}`,
        specializationIds: selectedSpecializationIds
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
      setImageDirty(false)
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

  const closeCropModal = () => {
    if (cropSource) {
      URL.revokeObjectURL(cropSource)
    }
    setCropOpen(false)
    setCropSource('')
    setCrop({ x: 0, y: 0 })
    setZoom(1)
    setCroppedAreaPixels(null)
  }

  async function handleProfileImageSelection(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return

    if (!file.type.startsWith('image/')) {
      setProfileError('Please choose a JPG or PNG image.')
      return
    }

    if (cropSource) {
      URL.revokeObjectURL(cropSource)
    }

    setProfileError('')
    setSuccessMessage('')
    setCropSource(URL.createObjectURL(file))
    setCrop({ x: 0, y: 0 })
    setZoom(1)
    setCroppedAreaPixels(null)
    setCropOpen(true)
  }

  const handleCropComplete = (_, croppedPixels) => {
    setCroppedAreaPixels(croppedPixels)
  }

  const handleUploadCroppedImage = async () => {
    if (!cropSource || !croppedAreaPixels) return

    setUploadingImage(true)
    setProfileError('')
    setSuccessMessage('')

    try {
      const blob = await getCroppedImage(cropSource, croppedAreaPixels)
      const croppedFile = new File([blob], 'doctor-profile.png', { type: 'image/png' })
      const updatedUser = await uploadDoctorProfileImage(croppedFile)
      
      // Log the full response to inspect structure
      console.log('[PROFILE_IMAGE] upload response:', updatedUser)
      console.log('[PROFILE_IMAGE] profileImageUrl from response:', updatedUser?.profileImageUrl)
      
      const newProfileImageUrl = updatedUser?.profileImageUrl
      console.log('[PROFILE_IMAGE] setting profile state with url:', newProfileImageUrl)
      
      setProfile((prev) => ({ ...prev, profileImageUrl: newProfileImageUrl }))
      updateUser(updatedUser)
      console.log('[PROFILE_IMAGE] context updated with user:', updatedUser)
      
      clearDoctorsCache()
      setSuccessMessage('Profile picture updated successfully.')
      setImageDirty(true)
      closeCropModal()
    } catch (err) {
      setProfileError(resolveErrorMessage(err, 'Failed to upload profile image.'))
    } finally {
      setUploadingImage(false)
    }
  }

  return (
      <DashboardLayout>
        <div className="space-y-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h1 className="text-2xl font-bold">Settings</h1>
              <p className="text-muted-foreground font-body">Manage your profile and specialization</p>
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
                  form="settings-form"
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

          {(profileError || securityError) && (
              <div className="space-y-2">
                {profileError && (
                    <div className="flex items-center gap-2 rounded-lg border border-destructive/20 bg-destructive/10 p-4 text-destructive">
                      <AlertCircle className="h-5 w-5" />
                      <p className="text-sm font-medium">{profileError}</p>
                    </div>
                )}
                {securityError && (
                    <div className="flex items-center gap-2 rounded-lg border border-destructive/20 bg-destructive/10 p-4 text-destructive">
                      <AlertCircle className="h-5 w-5" />
                      <p className="text-sm font-medium">{securityError}</p>
                    </div>
                )}
              </div>
          )}

          <div className="rounded-2xl border border-border bg-card p-6 shadow-card">
            <div className="flex items-center gap-5">
              <div className="relative">
                <UserAvatar
                  imageUrl={profile?.profileImageUrl}
                  name={`${user?.firstName || ''} ${user?.lastName || ''}`.trim()}
                  className="h-20 w-20"
                  textClassName="text-2xl"
                  toneClassName="bg-primary text-primary-foreground"
                  alt="Doctor profile"
                />

                <input
                  id="doctor-profile-image-input"
                  type="file"
                  accept="image/jpeg,image/png"
                  onChange={handleProfileImageSelection}
                  className="hidden"
                />
                <label
                  htmlFor="doctor-profile-image-input"
                  className="absolute -bottom-1 -right-1 cursor-pointer rounded-full border border-border bg-card p-1.5 shadow-md transition-colors hover:bg-muted"
                >
                  <Camera className="h-3.5 w-3.5 text-muted-foreground" />
                </label>
              </div>
              <div>
                <h2 className="text-xl font-bold">{user?.firstName} {user?.lastName}</h2>
                <p className="text-sm text-muted-foreground font-body">
                  {profile?.specializations?.join(', ') || 'General Practitioner'} • {user?.email}
                </p>
                {uploadingImage && <p className="mt-1 text-xs text-muted-foreground">Uploading profile image...</p>}
              </div>
            </div>
          </div>

          <form id="settings-form" onSubmit={handleSubmit} className="space-y-6">
            <div className="rounded-2xl border border-border bg-card shadow-card overflow-hidden">
              <div className="flex items-center gap-2 border-b border-border p-5">
                <User className="h-4 w-4 text-primary" />
                <h2 className="font-semibold text-lg">Profile Information</h2>
              </div>
              <div className="p-5 space-y-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <input name="firstName" value={form.firstName} onChange={updateField} placeholder="First Name" className="h-10 border rounded-md px-3 text-sm focus:ring-2 focus:ring-primary/20 outline-none" />
                  <input name="lastName" value={form.lastName} onChange={updateField} placeholder="Last Name" className="h-10 border rounded-md px-3 text-sm focus:ring-2 focus:ring-primary/20 outline-none" />
                </div>
                <input name="email" value={form.email} onChange={updateField} className={`h-10 w-full border rounded-md px-3 text-sm outline-none ${form.email && !isEmailValid ? 'border-destructive bg-destructive/5' : 'border-input'}`} placeholder="Email" />
                <div className={`flex h-10 items-center border rounded-md ${isPhoneValid ? 'border-teal' : 'border-input'}`}>
                  <span className="px-3 text-sm text-muted-foreground border-r">+63</span>
                  <input name="phoneNumber" value={form.phoneNumber} onChange={updateField} maxLength={10} className="w-full px-3 text-sm outline-none bg-transparent" />
                </div>

                <div className="space-y-2 pt-2">
                  <label className="text-sm font-medium">Specializations</label>
                  <div className="flex flex-wrap gap-2 rounded-md border p-3 bg-muted/30">
                    {allSpecializations.map((spec) => (
                        <button
                            key={spec.id}
                            type="button"
                            onClick={() => toggleSpecialization(spec.id)}
                            className={`rounded-full border px-3 py-1 text-sm transition-all ${selectedSpecializationIds.includes(spec.id) ? 'bg-primary text-white border-primary' : 'bg-card text-muted-foreground'}`}
                        >
                          {spec.name}
                        </button>
                    ))}
                  </div>
                </div>
              </div>
            </div>

            <div className="rounded-2xl border border-border bg-card shadow-card overflow-hidden">
              <div className="flex items-center justify-between border-b p-5">
                <div className="flex items-center gap-2">
                  <Shield className="h-4 w-4 text-teal" />
                  <h2 className="font-semibold text-lg">Security</h2>
                </div>
                <button type="button" onClick={() => setShowPasswords(!showPasswords)} className="text-xs text-muted-foreground">{showPasswords ? 'Hide' : 'Show'}</button>
              </div>
              <div className="p-5 space-y-4">
                <input
                    type={showPasswords ? "text" : "password"} placeholder="Current Password"
                    value={passwordForm.currentPassword} onChange={(e) => setPasswordForm(p => ({...p, currentPassword: e.target.value}))}
                    className="h-10 w-full border rounded-md px-3 text-sm outline-none"
                />
                <div className="grid gap-4 sm:grid-cols-2">
                  <input
                      type={showPasswords ? "text" : "password"} placeholder="New Password"
                      value={passwordForm.newPassword} onChange={(e) => setPasswordForm(p => ({...p, newPassword: e.target.value}))}
                      className={`h-10 border rounded-md px-3 text-sm outline-none ${passwordForm.newPassword && !isPasswordValid ? 'border-destructive' : 'border-input'}`}
                  />
                  <input
                      type={showPasswords ? "text" : "password"} placeholder="Confirm Password"
                      value={passwordForm.confirmPassword} onChange={(e) => setPasswordForm(p => ({...p, confirmPassword: e.target.value}))}
                      className={`h-10 border rounded-md px-3 text-sm outline-none ${passwordForm.confirmPassword && !doPasswordsMatch ? 'border-destructive' : 'border-input'}`}
                  />
                </div>
                {passwordForm.newPassword && (
                    <div className="grid grid-cols-2 gap-1">
                      <ValidationItem label="8+ Characters" met={passwordRules.length} />
                      <ValidationItem label="Uppercase" met={passwordRules.uppercase} />
                      <ValidationItem label="Number" met={passwordRules.number} />
                      <ValidationItem label="Special Symbol" met={passwordRules.special} />
                    </div>
                )}
              </div>
            </div>
          </form>

          {cropOpen && cropSource && (
              <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 px-4 py-8 backdrop-blur-sm">
                <div className="w-full max-w-2xl overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
                  <div className="flex items-center justify-between border-b border-border px-5 py-4">
                    <div>
                      <h3 className="text-lg font-semibold">Adjust profile photo</h3>
                    </div>
                    <button
                        type="button"
                        onClick={closeCropModal}
                        className="rounded-full p-2 text-muted-foreground hover:bg-muted"
                        aria-label="Close cropper"
                    >
                      <XCircle className="h-5 w-5" />
                    </button>
                  </div>

                  <div className="space-y-4 p-5">
                    <div className="relative h-72 w-full overflow-hidden rounded-2xl bg-slate-950">
                      <Cropper
                          image={cropSource}
                          crop={crop}
                          zoom={zoom}
                          aspect={1}
                          cropShape="round"
                          showGrid={false}
                          onCropChange={setCrop}
                          onZoomChange={setZoom}
                          onCropComplete={handleCropComplete}
                      />
                    </div>

                    <div className="space-y-2">
                      <div className="flex items-center justify-between text-xs text-muted-foreground">
                        <span>Zoom</span>
                        <span>{Math.round(zoom * 100)}%</span>
                      </div>
                      <input
                          type="range"
                          min="1"
                          max="3"
                          step="0.01"
                          value={zoom}
                          onChange={(event) => setZoom(Number(event.target.value))}
                          className="w-full accent-primary"
                      />
                    </div>
                  </div>

                  <div className="flex flex-col-reverse gap-3 border-t border-border px-5 py-4 sm:flex-row sm:justify-end">
                    <button
                        type="button"
                        onClick={closeCropModal}
                        className="inline-flex h-10 items-center justify-center rounded-md border border-input bg-background px-4 text-sm font-medium hover:bg-muted"
                    >
                      Cancel
                    </button>
                    <button
                        type="button"
                        onClick={handleUploadCroppedImage}
                        disabled={uploadingImage || !croppedAreaPixels}
                        className="inline-flex h-10 items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-primary-foreground shadow-md hover:bg-primary/90 disabled:opacity-50"
                    >
                      {uploadingImage ? 'Uploading...' : 'Use Photo'}
                    </button>
                  </div>
                </div>
              </div>
          )}
        </div>
      </DashboardLayout>
  )
}