/** @deprecated Prefer types/APIs from `@/api/client`. Kept for transitional imports. */
export type {
  FrigateBindDirection,
  FrigateCameraView as FrigateCamera,
  FrigateLinkStatus,
  FrigateSettingsView as FrigateServer,
} from '@/api/client'

export { frigateEventTopic as eventTopicOf, listFrigateCamerasApi as listFrigateCameras } from '@/api/client'
